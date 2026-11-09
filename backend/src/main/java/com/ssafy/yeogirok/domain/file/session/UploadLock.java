package com.ssafy.yeogirok.domain.file.session;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;

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

	private final RedisLock redisLock;
	private final IdempotencyProperties idempotencyProperties;

	/** @return 풀 때 넘길 값 */
	public String acquire(String uploadId) {
		return redisLock.tryAcquire(PREFIX + uploadId, idempotencyProperties.processingTimeout())
			.orElseThrow(() -> new BusinessException(ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS));
	}

	public void release(String uploadId, String token) {
		redisLock.release(PREFIX + uploadId, token);
	}
}
