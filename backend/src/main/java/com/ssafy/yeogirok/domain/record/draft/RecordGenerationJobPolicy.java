package com.ssafy.yeogirok.domain.record.draft;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobAccessPolicy;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** AI 기록 초안 작업(API-064)은 요청자와 상관없이 그 기록의 현재 참여자 모두 볼 수 있다(노션 API-011). */
@Component
@RequiredArgsConstructor
public class RecordGenerationJobPolicy implements JobAccessPolicy {

	public static final String TYPE = "RECORD_GENERATION";

	private final RecordAccess recordAccess;

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public void check(Long userId, Job job) {
		if (!recordAccess.isCurrentMember(Long.valueOf(job.resourceId()), userId)) {
			throw new BusinessException(ExceptionType.JOB_RESOURCE_FORBIDDEN);
		}
	}
}
