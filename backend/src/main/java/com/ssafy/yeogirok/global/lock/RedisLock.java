package com.ssafy.yeogirok.global.lock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Redis SET NX 잠금(업로드 완료 {@code upload-lock:*}·팁 수정 예약 {@code tip-edit-lock:*}). 수명이 지나면 저절로 풀려
 * 서버가 처리 도중 죽어도 남지 않는다. 내가 건 잠금만 푼다.
 */
@Component
@RequiredArgsConstructor
public class RedisLock {

	private static final RedisScript<Long> RELEASE = RedisScript.of("""
		if redis.call('get', KEYS[1]) == ARGV[1] then
		  return redis.call('del', KEYS[1])
		end
		return 0
		""", Long.class);

	private final StringRedisTemplate redis;

	/** @return 풀 때 넘길 값. 이미 잠겨 있으면 empty */
	public Optional<String> tryAcquire(String key, Duration ttl) {
		String token = UUID.randomUUID().toString();
		return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl)) ? Optional.of(token)
			: Optional.empty();
	}

	public void release(String key, String token) {
		redis.execute(RELEASE, List.of(key), token);
	}
}
