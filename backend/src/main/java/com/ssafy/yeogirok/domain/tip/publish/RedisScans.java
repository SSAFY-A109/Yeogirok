package com.ssafy.yeogirok.domain.tip.publish;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 패턴에 맞는 Redis 키를 SCAN 으로 훑어 값에서 하나씩 뽑는다(팁 작업 상태·수정안의 사진 경로). */
final class RedisScans {

	private RedisScans() {
	}

	/** @param pick 값 하나에서 뽑을 것. null 이면 건너뛴다 */
	static Set<String> collect(StringRedisTemplate redis, String pattern, Function<String, String> pick) {
		Set<String> picked = new HashSet<>();
		try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
			keys.forEachRemaining(key -> Optional.ofNullable(redis.opsForValue().get(key)).map(pick)
				.ifPresent(picked::add));
		}
		return picked;
	}
}
