package com.ssafy.yeogirok.domain.course.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.course.dto.CourseDetailResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 공개된 코스 상세(API-016, 명세 {@code specs/course-search-api.md} 1). 로그인한 사용자면 원본 방 참여자가 아니어도 본다.
 * 기록이 DRAFT 여도 게시 중이면 보인다(노션 016). 사진은 공개 선택된 연결 사진만.
 */
@Service
@RequiredArgsConstructor
public class CourseDetailService {

	private final TravelRecordRepository travelRecordRepository;
	private final TravelRoomRepository travelRoomRepository;
	private final PublicCourseReader publicCourseReader;
	private final CourseAuthors courseAuthors;

	/** 없음·방 삭제·미게시는 구분하지 않고 CO001. */
	@Transactional(readOnly = true)
	public CourseDetailResponse getDetail(Long courseId) {
		TravelRecord record = travelRecordRepository.findById(courseId)
			.filter(found -> found.getPublishedAt() != null)
			.orElseThrow(() -> new BusinessException(ExceptionType.COURSE_UNAVAILABLE));
		TravelRoom room = travelRoomRepository.findByIdAndDeletedAtIsNull(record.getRoomId())
			.orElseThrow(() -> new BusinessException(ExceptionType.COURSE_UNAVAILABLE));
		CourseSnapshot snapshot = publicCourseReader.snapshot(courseId);
		List<ActualVisit> visits = snapshot.visits();
		CoursePeriod period = CoursePeriod.of(room.getStartDate(), room.getEndDate(),
			visits.isEmpty() ? null : visits.getFirst().getVisitDate(),
			visits.isEmpty() ? null : visits.getLast().getVisitDate());
		return new CourseDetailResponse(record.getId(), record.getTitle(), record.getDescription(),
			courseAuthors.of(room.getId()),
			publicCourseReader.days(snapshot, room.getStartDate(), PhotoScope.PUBLIC_SELECTED),
			period.dayCount(), period.nightCount(), room.getTransportMode(), record.getVersion());
	}
}
