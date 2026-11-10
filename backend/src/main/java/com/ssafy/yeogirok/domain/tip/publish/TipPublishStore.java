package com.ssafy.yeogirok.domain.tip.publish;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
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

	/** 저장된 작업의 jobId 가 ARGV[1] 일 때만 바꾼다. */
	private static final RedisScript<Long> SAVE_IF_CURRENT = RedisScript.of("""
		local current = redis.call('get', KEYS[1])
		if current and cjson.decode(current).jobId == ARGV[1] then
		  redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3])
		  return 1
		end
		return 0
		""", Long.class);

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final IdempotencyProperties idempotencyProperties;

	public void save(Long tipId, TipPublishState state) {
		redis.opsForValue().set(PREFIX + tipId, jsonMapper.writeValueAsString(state), idempotencyProperties.retention());
	}

	/**
	 * 같은 팁의 최신 작업이 아직 {@code state.jobId()} 일 때만 저장한다. 늦게 돈 앞 작업이 새 작업 상태를 덮지 않게 한다.
	 * @return 저장했으면 true
	 */
	public boolean saveIfCurrent(Long tipId, TipPublishState state) {
		return Long.valueOf(1).equals(redis.execute(SAVE_IF_CURRENT, List.of(PREFIX + tipId), state.jobId(),
			jsonMapper.writeValueAsString(state), String.valueOf(idempotencyProperties.retention().toMillis())));
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
