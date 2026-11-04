package com.ssafy.yeogirok.domain.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 서비스 리프레시 토큰. JWT 가 아닌 불투명한 랜덤 문자열이고, 원문 대신 SHA-256 해시로 Redis 에 둔다(Notion ERD › Redis §2).
 *
 * <ul>
 *   <li>{@code auth:refresh:{해시}} — userId·sessionId·status·expiresAt. 토큰 만료까지 둔다.
 *       재발급에 쓰인 토큰은 {@code ROTATED} 로 남겨 다시 오면 탈취로 본다.</li>
 *   <li>{@code auth:session:{sessionId}} — userId·revoked. 로그인 한 번(기기 하나)이 세션 하나다. 재발급할 때 수명을 늘린다.</li>
 *   <li>{@code auth:user-sessions:{userId}} — 그 회원의 세션 ID 목록(Set). 탈퇴할 때 모든 기기 세션을 찾는다.</li>
 * </ul>
 *
 * <p>재발급의 확인·회전은 Lua 스크립트 하나로 처리한다. 같은 토큰으로 동시에 재발급해도 한 번만 회전한다.
 * 만료된 키는 Redis 가 지우므로 만료 뒤에 온 토큰은 대개 모르는 토큰(A007)이다. A008 은 만료 직후 키가 지워지기 전에만 난다.
 *
 * <p>Redis 에 연결할 수 없으면 예외가 그대로 올라가 500(C014)이 된다. 만료·폐기(401)와 구분된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private static final int TOKEN_BYTES = 32;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private static final String TOKEN_KEY = "auth:refresh:";
	private static final String SESSION_KEY = "auth:session:";
	private static final String USER_SESSIONS_KEY = "auth:user-sessions:";

	/**
	 * 로그인. 새 세션과 토큰을 만들고 회원의 세션 목록에 넣는다. 이미 만료된 세션 ID는 목록에서 뺀다.
	 * KEYS: 토큰, 세션, 세션 목록 / ARGV: userId, sessionId, 만료 시각(epoch 초), 수명(초)
	 */
	private static final RedisScript<Long> ISSUE = RedisScript.of("""
		redis.call('hset', KEYS[1], 'userId', ARGV[1], 'sessionId', ARGV[2], 'status', 'ACTIVE', 'expiresAt', ARGV[3])
		redis.call('expire', KEYS[1], ARGV[4])
		redis.call('hset', KEYS[2], 'userId', ARGV[1], 'revoked', '0')
		redis.call('expire', KEYS[2], ARGV[4])
		for _, id in ipairs(redis.call('smembers', KEYS[3])) do
		  if redis.call('exists', 'auth:session:' .. id) == 0 then
		    redis.call('srem', KEYS[3], id)
		  end
		end
		redis.call('sadd', KEYS[3], ARGV[2])
		if redis.call('ttl', KEYS[3]) < tonumber(ARGV[4]) then
		  redis.call('expire', KEYS[3], ARGV[4])
		end
		return 1
		""", Long.class);

	/**
	 * 재발급. 순서가 중요하다 — 세션 폐기·재사용을 만료보다 먼저 본다. 탈취된 토큰은 만료와 상관없이 세션을 끊어야 한다.
	 * KEYS: 이전 토큰, 새 토큰 / ARGV: 지금(epoch 초), 새 토큰 만료 시각(epoch 초), 수명(초)
	 * 반환: {INVALID} | {REVOKED} | {REUSED, userId, sessionId} | {EXPIRED} | {OK, userId}
	 */
	@SuppressWarnings("rawtypes")
	private static final RedisScript<List> ROTATE = RedisScript.of("""
		local token = redis.call('hmget', KEYS[1], 'userId', 'sessionId', 'status', 'expiresAt')
		if not token[1] then return {'INVALID'} end
		local sessionKey = 'auth:session:' .. token[2]
		local revoked = redis.call('hget', sessionKey, 'revoked')
		if not revoked then return {'INVALID'} end
		if revoked == '1' then return {'REVOKED'} end
		if token[3] == 'ROTATED' then
		  redis.call('hset', sessionKey, 'revoked', '1')
		  return {'REUSED', token[1], token[2]}
		end
		if tonumber(token[4]) <= tonumber(ARGV[1]) then return {'EXPIRED'} end
		redis.call('hset', KEYS[1], 'status', 'ROTATED')
		redis.call('hset', KEYS[2], 'userId', token[1], 'sessionId', token[2], 'status', 'ACTIVE', 'expiresAt', ARGV[2])
		redis.call('expire', KEYS[2], ARGV[3])
		redis.call('expire', sessionKey, ARGV[3])
		local userSessions = 'auth:user-sessions:' .. token[1]
		if redis.call('ttl', userSessions) < tonumber(ARGV[3]) then
		  redis.call('expire', userSessions, ARGV[3])
		end
		return {'OK', token[1]}
		""", List.class);

	/**
	 * 로그아웃. 토큰이 이 회원의 것일 때만 그 세션을 폐기한다.
	 * KEYS: 토큰 / ARGV: userId
	 */
	private static final RedisScript<Long> REVOKE = RedisScript.of("""
		local token = redis.call('hmget', KEYS[1], 'userId', 'sessionId')
		if token[1] ~= ARGV[1] then return 0 end
		local sessionKey = 'auth:session:' .. token[2]
		if redis.call('exists', sessionKey) == 0 then return 0 end
		redis.call('hset', sessionKey, 'revoked', '1')
		return 1
		""", Long.class);

	/**
	 * 탈퇴. 회원의 모든 세션을 폐기하고 세션 목록을 지운다. 폐기한 세션 수를 돌려준다.
	 * KEYS: 세션 목록
	 */
	private static final RedisScript<Long> REVOKE_ALL = RedisScript.of("""
		local count = 0
		for _, id in ipairs(redis.call('smembers', KEYS[1])) do
		  local sessionKey = 'auth:session:' .. id
		  if redis.call('hget', sessionKey, 'revoked') == '0' then
		    redis.call('hset', sessionKey, 'revoked', '1')
		    count = count + 1
		  end
		end
		redis.call('del', KEYS[1])
		return count
		""", Long.class);

	private final StringRedisTemplate redis;
	private final JwtProperties properties;
	private final Clock clock;

	public record Rotated(
		Long userId,
		String rawToken
	) {
	}

	/** 로그인 시 새 세션을 시작한다. */
	public String issueForNewLogin(Long userId) {
		String rawToken = generate();
		String sessionId = UUID.randomUUID().toString();
		long now = clock.instant().getEpochSecond();

		redis.execute(ISSUE, List.of(TOKEN_KEY + hash(rawToken), SESSION_KEY + sessionId, USER_SESSIONS_KEY + userId),
			String.valueOf(userId), sessionId, String.valueOf(now + ttlSeconds()), String.valueOf(ttlSeconds()));
		return rawToken;
	}

	/** 재발급. 재사용을 감지하면 그 세션을 폐기하고 A010 을 던진다. */
	public Rotated rotate(String rawToken) {
		String newRawToken = generate();
		long now = clock.instant().getEpochSecond();

		List<?> result = redis.execute(ROTATE, List.of(TOKEN_KEY + hash(rawToken), TOKEN_KEY + hash(newRawToken)),
			String.valueOf(now), String.valueOf(now + ttlSeconds()), String.valueOf(ttlSeconds()));

		return switch (String.valueOf(result.getFirst())) {
			case "OK" -> new Rotated(Long.valueOf(result.get(1).toString()), newRawToken);
			case "REVOKED" -> throw new BusinessException(ExceptionType.REFRESH_TOKEN_REVOKED);
			case "EXPIRED" -> throw new BusinessException(ExceptionType.REFRESH_TOKEN_EXPIRED);
			case "REUSED" -> {
				log.warn("Rotated refresh token reused. Revoking session. userId={}, sessionId={}", result.get(1),
					result.get(2));
				throw new BusinessException(ExceptionType.REFRESH_TOKEN_REUSED);
			}
			default -> throw new BusinessException(ExceptionType.REFRESH_TOKEN_INVALID);
		};
	}

	/**
	 * 로그아웃. 그 토큰이 속한 세션(이 기기)을 폐기한다.
	 * 없는 토큰·이미 폐기된 토큰·다른 사람의 토큰이어도 조용히 성공한다(멱등, 존재 여부를 알려 주지 않는다).
	 */
	public void revoke(Long userId, String rawToken) {
		if (!StringUtils.hasText(rawToken)) {
			return;
		}

		redis.execute(REVOKE, List.of(TOKEN_KEY + hash(rawToken)), String.valueOf(userId));
	}

	/** 탈퇴 시 모든 기기 세션을 폐기한다. */
	public long revokeAllSessions(Long userId) {
		return redis.execute(REVOKE_ALL, List.of(USER_SESSIONS_KEY + userId));
	}

	public long ttlSeconds() {
		return properties.refreshTokenTtl().toSeconds();
	}

	private static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없습니다.", e);
		}
	}
}
