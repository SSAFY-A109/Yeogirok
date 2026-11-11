package com.ssafy.yeogirok.domain.course.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CoursePublicationResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 확인한 여행 기록을 공개 코스로 게시(API-073). 멱등키는 컨트롤러가 이 밖에서 처리한다. */
@Service
@RequiredArgsConstructor
public class CoursePublishService {

	private final RecordFinder recordFinder;
	private final PublicCourseReader publicCourseReader;
	private final CourseSelection courseSelection;
	private final Clock clock;

	/**
	 * 기록 행을 잠근 뒤 권한(RE001·RE003) → 게시 중 CO011 → 미완성 RE019 → version·해시 불일치 CO010(명세 4-3).
	 * version 이 달라도 C004 가 아니라 CO010 이다(노션 073 오류표·프론트 기준, 명세 5절 11).
	 */
	@Transactional
	public CoursePublicationResponse publish(Long userId, Long recordId, CourseInput input) {
		TravelRecord record = recordFinder.lockWritable(recordId, userId).record();
		if (record.getPublishedAt() != null) {
			throw new BusinessException(ExceptionType.COURSE_ALREADY_PUBLISHED);
		}
		if (!record.isCompleted()) {
			throw new BusinessException(ExceptionType.RECORD_NOT_PUBLISHABLE);
		}
		String description = input.description() == null ? record.getDescription() : input.description();
		CourseSnapshot snapshot = publicCourseReader.snapshot(recordId);
		if (record.getVersion() != input.version() || !SelectionHash.of(recordId, record.getVersion(), input.title(),
			description, input.includePhotos(), snapshot).equals(input.selectionHash())) {
			throw new BusinessException(ExceptionType.COURSE_SELECTION_CHANGED);
		}
		boolean selectionChanged = courseSelection.save(recordId, input.includePhotos());
		Instant now = clock.instant();
		record.changeCourse(input.title(), description, now, selectionChanged, now);
		return CoursePublicationResponse.from(record);
	}

	/** 같은 멱등키 재전송. 현재 쓰기 권한을 다시 확인하고 지금 기록 값을 준다. */
	@Transactional(readOnly = true)
	public CoursePublicationResponse getPublished(Long userId, Long recordId) {
		return CoursePublicationResponse.from(recordFinder.checkWritable(recordId, userId));
	}
}
