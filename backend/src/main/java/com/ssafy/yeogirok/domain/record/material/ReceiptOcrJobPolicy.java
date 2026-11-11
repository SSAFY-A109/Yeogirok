package com.ssafy.yeogirok.domain.record.material;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobAccessPolicy;
import com.ssafy.yeogirok.domain.job.service.JobService;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 영수증 OCR 작업(API-062·092)은 업로더 본인만, 그리고 지금도 그 기록 참여자일 때만 볼 수 있다. */
@Component
@RequiredArgsConstructor
public class ReceiptOcrJobPolicy implements JobAccessPolicy {

	private final RecordAccess recordAccess;

	@Override
	public String type() {
		return ReceiptOcrRunner.TYPE;
	}

	@Override
	public void check(Long userId, Job job) {
		JobService.requireRequester(userId, job);
		if (!recordAccess.isCurrentMember(Long.valueOf(job.resourceId()), userId)) {
			throw new BusinessException(ExceptionType.JOB_RESOURCE_FORBIDDEN);
		}
	}
}
