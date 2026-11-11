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

/** 공개 코스 내용 수정·재게시·공개 해제(API-075, 명세 {@code specs/course-publish-api.md} 4-5). courseId 는 기록 ID. */
@Service
@RequiredArgsConstructor
public class CourseUpdateService {

	private final RecordFinder recordFinder;
	private final PublicCourseReader publicCourseReader;
	private final CourseSelection courseSelection;
	private final Clock clock;

	/**
	 * 기록 행 잠금 뒤 CO013 → CO008 → C004 → (내용 변경·재게시면) 해시·includePhotos 없음 C001 → 해시 불일치 CO010 → (재게시면)
	 * 미완성 RE019.
	 * 해제만(published=false)은 해시 없이 된다. 실제로 바뀐 것이 있을 때만 version 을 한 번 올린다. status 는 바꾸지 않는다.
	 */
	@Transactional
	public CoursePublicationResponse update(Long userId, Long courseId, CourseInput input) {
		TravelRecord record = recordFinder.lockCourse(courseId, userId);
		if (record.getVersion() != input.version()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		boolean published = record.getPublishedAt() != null;
		boolean publishing = Boolean.TRUE.equals(input.published()) && !published;
		boolean unpublishing = Boolean.FALSE.equals(input.published()) && published;
		String title = input.title() == null ? record.getTitle() : input.title();
		String description = input.description() == null ? record.getDescription() : input.description();

		boolean selectionChanged = false;
		if (input.changesContent() || publishing) {
			if (input.selectionHash() == null) {
				throw new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING,
					"selectionHash: 내용을 바꾸거나 다시 게시하려면 필요합니다.");
			}
			// 앱이 072 미리보기 때 정한 값을 그대로 보낸다. 서버가 추정하면 앱이 맞출 수 없어 CO010 이 풀리지 않았다(PR #89 리뷰).
			if (input.includePhotos() == null) {
				throw new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING,
					"includePhotos: 내용을 바꾸거나 다시 게시하려면 필요합니다.");
			}
			boolean includePhotos = input.includePhotos();
			CourseSnapshot snapshot = publicCourseReader.snapshot(courseId);
			if (!SelectionHash.of(courseId, record.getVersion(), title, description, includePhotos, snapshot)
				.equals(input.selectionHash())) {
				throw new BusinessException(ExceptionType.COURSE_SELECTION_CHANGED);
			}
			if (publishing && !record.isCompleted()) {
				throw new BusinessException(ExceptionType.RECORD_NOT_PUBLISHABLE);
			}
			selectionChanged = courseSelection.save(courseId, includePhotos);
		}
		Instant now = clock.instant();
		Instant publishedAt = publishing ? now : unpublishing ? null : record.getPublishedAt();
		record.changeCourse(title, description, publishedAt, selectionChanged, now);
		return CoursePublicationResponse.from(record);
	}
}
