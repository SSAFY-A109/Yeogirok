package com.ssafy.yeogirok.domain.tip.publish;

import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * API-054 수정안(Redis {@code tip-edit:{tipId}:{jobId}})과 팁별 수정 예약({@code tip-edit-lock:{tipId}}).
 * 수정안은 051 재시도가 다시 쓸 수 있게 멱등키 보관 기간만큼 두고, 예약은 멱등키 처리 중 표시 수명만큼 둔다(11/9 결정).
 */
@Component
@RequiredArgsConstructor
public class TipEditStore {

	private static final String DRAFT_PREFIX = "tip-edit:";
	private static final String LOCK_PREFIX = "tip-edit-lock:";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final RedisLock redisLock;
	private final IdempotencyProperties idempotencyProperties;

	public void save(Long tipId, String jobId, TipEditDraft draft) {
		redis.opsForValue().set(draftKey(tipId, jobId), jsonMapper.writeValueAsString(draft),
			idempotencyProperties.retention());
	}

	public Optional<TipEditDraft> find(Long tipId, String jobId) {
		return Optional.ofNullable(redis.opsForValue().get(draftKey(tipId, jobId)))
			.map(value -> jsonMapper.readValue(value, TipEditDraft.class));
	}

	public void delete(Long tipId, String jobId) {
		redis.delete(draftKey(tipId, jobId));
	}

	/** @return 풀 때 넘길 값. 다른 수정이 예약돼 있으면 empty */
	public Optional<String> reserve(Long tipId) {
		return redisLock.tryAcquire(LOCK_PREFIX + tipId, idempotencyProperties.processingTimeout());
	}

	public void release(Long tipId, String token) {
		redisLock.release(LOCK_PREFIX + tipId, token);
	}

	private static String draftKey(Long tipId, String jobId) {
		return DRAFT_PREFIX + tipId + ":" + jobId;
	}
}
