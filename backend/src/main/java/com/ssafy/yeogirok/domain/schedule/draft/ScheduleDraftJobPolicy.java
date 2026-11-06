package com.ssafy.yeogirok.domain.schedule.draft;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobAccessPolicy;
import com.ssafy.yeogirok.domain.job.service.JobService;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 일정 초안 작업(API-037·084)은 요청자 본인만, 그리고 지금도 그 여행방 참여자일 때만 볼 수 있다. */
@Component
@RequiredArgsConstructor
public class ScheduleDraftJobPolicy implements JobAccessPolicy {

	public static final String TYPE = "SCHEDULE_DRAFT";

	private final RoomFinder roomFinder;

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public void check(Long userId, Job job) {
		JobService.requireRequester(userId, job);
		try {
			roomFinder.getForMember(Long.valueOf(job.resourceId()), userId);
		} catch (BusinessException e) {
			throw new BusinessException(ExceptionType.JOB_RESOURCE_FORBIDDEN);
		}
	}
}
