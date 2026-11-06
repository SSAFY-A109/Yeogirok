package com.ssafy.yeogirok.domain.file.session;

import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;

import lombok.RequiredArgsConstructor;

/**
 * uploadId 별 완료 처리 잠금(Redis {@code upload-lock:{uploadId}}, SET NX). 공통 멱등키는 같은 키끼리만 막으므로
 * 서로 다른 멱등키로 동시에 온 완료 요청을 여기서 한 줄로 세운다. 이미 잠겨 있으면 C018.
 * 수명은 멱등키 처리 중 표시와 같다(서버가 처리 도중 죽어도 풀린다).
 */
@Component
@RequiredArgsConstructor
public class UploadLock {

	private static final String PREFIX = "upload-lock:";

	/** 내가 건 잠금일 때만 지운다. */
	private static final RedisScript<Long> RELEASE = RedisScript.of("""
		if redis.call('get', KEYS[1]) == ARGV[1] then
		  return redis.call('del', KEYS[1])
		end
		return 0
		""", Long.class);

	private final StringRedisTemplate redis;
	private final IdempotencyProperties idempotencyProperties;

	/** @return 풀 때 넘길 값 */
	public String acquire(String uploadId) {
		String token = UUID.randomUUID().toString();
		if (!Boolean.TRUE.equals(redis.opsForValue()
			.setIfAbsent(PREFIX + uploadId, token, idempotencyProperties.processingTimeout()))) {
			throw new BusinessException(ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS);
		}
		return token;
	}

	public void release(String uploadId, String token) {
		redis.execute(RELEASE, List.of(PREFIX + uploadId), token);
	}
}
