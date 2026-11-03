package com.ssafy.yeogirok.global.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공통 멱등키 처리. {@code Idempotency-Key} 를 받는 API 는 모두 이것을 쓴다(따로 만들지 않는다).
 *
 * <ol>
 *   <li>Redis 에 '처리 중' 표시를 SET NX 로 건다. 건 요청만 실제 처리를 한다.</li>
 *   <li>처리가 끝나면 값을 {@code {요청 해시, 결과 ID}} 로 바꾸고 보관 기간 동안 둔다.</li>
 * </ol>
 *
 * <p>표시를 못 건 요청은 저장된 값으로 판단한다.
 * <ul>
 *   <li>요청 내용이 다르면 → C005</li>
 *   <li>같은 요청이 끝났으면 → 저장된 결과 ID로 다시 조회해 돌려준다</li>
 * </ul>
 *
 * <p>Redis 키가 남아 있다는 것만으로 DB 중복 반영을 막았다고 보지 않는다. DB 유일 제약·version 으로도 막는다.
 *
 * <p><b>트랜잭션 밖에서 부른다.</b> {@code action} 안의 트랜잭션이 커밋된 뒤에 결과 ID를 저장해야,
 * 다시 보낸 요청이 아직 커밋되지 않은 결과를 찾다가 실패하지 않는다.
 */
@Slf4j
@Component
public class IdempotencyService {

	/** 내 처리 중 표시가 그대로이거나 수명이 지나 사라졌을 때만 결과를 저장한다. */
	private static final RedisScript<Long> COMPLETE = RedisScript.of("""
		local current = redis.call('get', KEYS[1])
		if current == false or current == ARGV[1] then
		  redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3])
		  return 1
		end
		return 0
		""", Long.class);

	/** 표시를 못 걸었는데 값을 읽으니 그사이 사라진 경우에만 다시 시도한다. */
	private static final int MAX_ATTEMPTS = 3;

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	/** 요청 해시용. Map 은 키 순서로 직렬화해 같은 내용이면 같은 해시가 나오게 한다. */
	private final JsonMapper hashMapper;
	private final IdempotencyProperties properties;

	public IdempotencyService(StringRedisTemplate redis, JsonMapper jsonMapper, IdempotencyProperties properties) {
		this.redis = redis;
		this.jsonMapper = jsonMapper;
		this.hashMapper = jsonMapper.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
		this.properties = properties;
	}

	/**
	 * @param key            멱등키
	 * @param request        요청 내용. JSON 으로 바꿔 해시한다. 같은 키로 다른 내용이 오면 C005 다
	 * @param action         실제 처리. 처리 중 표시를 건 요청에서만 한 번 실행된다
	 * @param resultIdOf     처리 결과에서 다시 조회할 때 쓸 ID(자원 ID·jobId)를 꺼낸다
	 * @param findByResultId 같은 요청을 다시 보냈을 때 저장된 결과 ID로 결과를 다시 조회한다
	 */
	public <R> R execute(IdempotencyKey key, Object request, Supplier<R> action, Function<R, ?> resultIdOf,
		Function<String, R> findByResultId) {
		String redisKey = key.toRedisKey();
		String requestHash = hash(request);

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String marker = write(IdempotencyRecord.processing(requestHash));
			if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey, marker, properties.processingTimeout()))) {
				return runAndRecord(redisKey, marker, requestHash, action, resultIdOf);
			}

			String stored = redis.opsForValue().get(redisKey);
			if (stored == null) {
				continue;
			}
			IdempotencyRecord record = read(stored);
			if (!record.requestHash().equals(requestHash)) {
				throw new BusinessException(ExceptionType.IDEMPOTENCY_KEY_REUSED);
			}
			if (record.resultId() == null) {
				throw new IllegalStateException("같은 요청을 아직 처리하고 있습니다: " + redisKey);
			}
			return findByResultId.apply(record.resultId());
		}
		throw new IllegalStateException("멱등키 처리 중 표시를 반복해서 걸지 못했습니다: " + redisKey);
	}

	private <R> R runAndRecord(String redisKey, String marker, String requestHash, Supplier<R> action,
		Function<R, ?> resultIdOf) {
		R result = action.get();

		// 처리는 이미 끝났다. 여기서 Redis 저장이 실패해도 결과는 돌려주고, 처리 중 표시는 수명이 지나면 풀린다.
		try {
			Object resultId = resultIdOf.apply(result);
			if (resultId == null) {
				throw new IllegalStateException("처리 결과에 결과 ID가 없습니다: " + redisKey);
			}
			String completed = write(IdempotencyRecord.completed(requestHash, resultId.toString()));
			Long saved = redis.execute(COMPLETE, List.of(redisKey), marker, completed,
				String.valueOf(properties.retention().toMillis()));
			if (!Long.valueOf(1).equals(saved)) {
				log.warn("처리 중 표시 수명이 지나 다른 요청이 같은 멱등키를 쓰고 있어 결과를 저장하지 않았습니다: {}", redisKey);
			}
		} catch (RuntimeException e) {
			log.error("멱등키 결과 저장에 실패했습니다: {}", redisKey, e);
		}
		return result;
	}

	private String hash(Object request) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(hashMapper.writeValueAsBytes(request));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private String write(IdempotencyRecord record) {
		return jsonMapper.writeValueAsString(record);
	}

	private IdempotencyRecord read(String value) {
		return jsonMapper.readValue(value, IdempotencyRecord.class);
	}
}
