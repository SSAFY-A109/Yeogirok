package com.ssafy.yeogirok.domain.file.session;

import java.time.Instant;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 영수증 원본 정리 대상(Redis Sorted Set {@code material-cleanup:due}, member {@code {버킷}/{key}}, score 삭제 기한 Unix 초).
 * 정리 작업(specs/file-api.md 4-5)이 기한 지난 항목을 지운다. 등록에 실패하면 원본을 올리지 못하게 M012 로 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptCleanupQueue {

	static final String KEY = "material-cleanup:due";

	private final StringRedisTemplate redis;

	/** 삭제 기한이 {@code now} 이전인 대상({@code {버킷}/{key}}). */
	public Set<String> due(Instant now) {
		Set<String> members = redis.opsForZSet().rangeByScore(KEY, Double.NEGATIVE_INFINITY, now.getEpochSecond());
		return members == null ? Set.of() : members;
	}

	/** 삭제를 확인한 뒤에만 뺀다. */
	public void remove(String member) {
		redis.opsForZSet().remove(KEY, member);
	}

	/** 같은 대상을 다시 등록하면 기한만 덮어쓴다. */
	public void register(String bucket, String key, Instant deleteAfter) {
		try {
			redis.opsForZSet().add(KEY, bucket + "/" + key, deleteAfter.getEpochSecond());
		} catch (DataAccessException e) {
			log.error("영수증 정리 대상을 등록하지 못했습니다.", e);
			throw new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE);
		}
	}
}
