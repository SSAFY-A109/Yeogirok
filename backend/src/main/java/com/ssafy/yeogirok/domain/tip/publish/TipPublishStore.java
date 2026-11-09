package com.ssafy.yeogirok.domain.tip.publish;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 팁 게시 작업 상태 저장소(Redis {@code tip-publish:{tipId}}). 049 멱등키 재전송이 jobId 를 다시 찾아야 해서
 * 멱등키 보관 기간만큼 둔다(11/8 결정). 저장할 때마다 기간을 새로 센다.
 */
@Component
@RequiredArgsConstructor
public class TipPublishStore {

	private static final String PREFIX = "tip-publish:";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final IdempotencyProperties idempotencyProperties;

	public void save(Long tipId, TipPublishState state) {
		redis.opsForValue().set(PREFIX + tipId, jsonMapper.writeValueAsString(state), idempotencyProperties.retention());
	}

	public Optional<TipPublishState> find(Long tipId) {
		return Optional.ofNullable(redis.opsForValue().get(PREFIX + tipId))
			.map(value -> jsonMapper.readValue(value, TipPublishState.class));
	}

	/** 모든 {@code tip-publish:*} 의 사진 경로(팁에 안 붙은 사진 정리 때 지우지 않을 것). */
	public Set<String> imagePaths() {
		return RedisScans.collect(redis, PREFIX + "*",
			value -> jsonMapper.readValue(value, TipPublishState.class).imagePath());
	}
}
