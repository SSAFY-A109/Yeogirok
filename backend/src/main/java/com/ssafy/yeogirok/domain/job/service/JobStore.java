package com.ssafy.yeogirok.domain.job.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobProperties;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비동기 작업 저장소(Redis {@code job:{jobId}}). 키는 작업을 만든 시각부터 보관 기간만큼 둔다. 상태를 바꿔 저장해도
 * 기한은 늘지 않는다.
 */
@Component
@RequiredArgsConstructor
public class JobStore {

	private static final String PREFIX = "job:";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final JobProperties properties;
	private final Clock clock;

	/** 기한이 이미 지난 작업은 저장하지 않는다. */
	public void save(Job job) {
		Duration ttl = Duration.between(clock.instant(), job.createdAt().plus(properties.retention()));
		if (ttl.isNegative() || ttl.isZero()) {
			return;
		}
		redis.opsForValue().set(PREFIX + job.jobId(), jsonMapper.writeValueAsString(job), ttl);
	}

	public Optional<Job> find(String jobId) {
		return Optional.ofNullable(redis.opsForValue().get(PREFIX + jobId))
			.map(value -> jsonMapper.readValue(value, Job.class));
	}
}
