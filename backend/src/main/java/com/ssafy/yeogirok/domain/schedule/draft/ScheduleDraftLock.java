package com.ssafy.yeogirok.domain.schedule.draft;

import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 초안별 처리 잠금(Redis {@code schedule-draft-lock:{draftKey}}, SET NX). 같은 초안의 재계산(API-084)과 최종 저장(039)이
 * 겹치지 않게 한다. 이미 잠겨 있으면 SC003. 수명은 작업 제한 시간과 같다(서버가 처리 도중 재시작돼도 풀린다).
 */
@Component
@RequiredArgsConstructor
public class ScheduleDraftLock {

	private static final String PREFIX = "schedule-draft-lock:";

	/** 내가 건 잠금일 때만 지운다. */
	private static final RedisScript<Long> RELEASE = RedisScript.of("""
		if redis.call('get', KEYS[1]) == ARGV[1] then
		  return redis.call('del', KEYS[1])
		end
		return 0
		""", Long.class);

	private final StringRedisTemplate redis;
	private final JobProperties jobProperties;

	/** @return 풀 때 넘길 값 */
	public String acquire(String draftKey) {
		String token = UUID.randomUUID().toString();
		if (!Boolean.TRUE.equals(redis.opsForValue()
			.setIfAbsent(PREFIX + draftKey, token, jobProperties.runTimeout()))) {
			throw new BusinessException(ExceptionType.SCHEDULE_DRAFT_IN_PROGRESS);
		}
		return token;
	}

	public void release(String draftKey, String token) {
		redis.execute(RELEASE, List.of(PREFIX + draftKey), token);
	}
}
