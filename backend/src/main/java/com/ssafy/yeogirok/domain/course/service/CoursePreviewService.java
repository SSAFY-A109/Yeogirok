package com.ssafy.yeogirok.domain.course.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CoursePreviewResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.domain.record.service.RecordFinder.RecordRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 공개할 코스 미리보기(API-072). 아무것도 저장하지 않는다. 완성 전이어도 볼 수 있고 게시(073)에서 RE019 로 막는다. */
@Service
@RequiredArgsConstructor
public class CoursePreviewService {

	private final RecordFinder recordFinder;
	private final PublicCourseReader publicCourseReader;
	private final CourseAuthors courseAuthors;

	/** 권한(RE001·RE003) → version(C004). description 을 생략하면 지금 기록 설명을 쓴다. */
	@Transactional(readOnly = true)
	public CoursePreviewResponse preview(Long userId, Long recordId, CourseInput input) {
		RecordRoom found = recordFinder.getWritable(recordId, userId);
		TravelRecord record = found.record();
		if (record.getVersion() != input.version()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		String description = input.description() == null ? record.getDescription() : input.description();
		boolean includePhotos = input.includePhotos();
		CourseSnapshot snapshot = publicCourseReader.snapshot(recordId);
		CoursePreviewResponse.Preview preview = new CoursePreviewResponse.Preview(input.title(),
			courseAuthors.of(found.room().getId()), description, publicCourseReader.days(snapshot,
				found.room().getStartDate(), includePhotos ? PhotoScope.LINKED : PhotoScope.NONE));
		String hash = SelectionHash.of(recordId, record.getVersion(), input.title(), description, includePhotos,
			snapshot);
		return new CoursePreviewResponse(preview, hash, List.of(), List.of());
	}
}
