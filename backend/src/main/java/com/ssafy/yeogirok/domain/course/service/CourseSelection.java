package com.ssafy.yeogirok.domain.course.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 공개 선택 저장(명세 {@code specs/course-publish-api.md} 1-4). 방문은 전부 공개, 사진은 includePhotos 면 연결된 사진만 공개.
 * 기록 행을 잠근 트랜잭션 안에서 조건 UPDATE 두 번으로 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class CourseSelection {

	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;

	/** @return 공개 선택이 바뀐 행이 있으면 true */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean save(Long recordId, boolean includePhotos) {
		int visits = actualVisitRepository.selectAllPublic(recordId);
		int photos = visitImageRepository.selectPublic(recordId, includePhotos);
		return visits + photos > 0;
	}
}
