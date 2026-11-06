package com.ssafy.yeogirok.domain.file.session;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 업로드 세션 저장소(Redis {@code upload:{uploadId}}).
 *
 * <p>키는 최초 발급 시각부터 멱등키 보관 기간만큼 둔다. 같은 멱등키로 다시 보낸 012·086 이 세션을 찾아야 해서
 * 멱등키보다 먼저 사라지면 안 된다. 갱신해도 기한은 늘지 않는다.
 */
@Component
@RequiredArgsConstructor
public class UploadSessionStore {

	private static final String PREFIX = "upload:";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final IdempotencyProperties idempotencyProperties;
	private final Clock clock;

	/** 기한이 이미 지난 세션은 저장하지 않는다. */
	public void save(UploadSession session) {
		Duration ttl = Duration.between(clock.instant(),
			session.uploadAcceptedAt().plus(idempotencyProperties.retention()));
		if (ttl.isNegative() || ttl.isZero()) {
			return;
		}
		redis.opsForValue().set(PREFIX + session.uploadId(), jsonMapper.writeValueAsString(session), ttl);
	}

	public Optional<UploadSession> find(String uploadId) {
		return Optional.ofNullable(redis.opsForValue().get(PREFIX + uploadId))
			.map(value -> jsonMapper.readValue(value, UploadSession.class));
	}
}
