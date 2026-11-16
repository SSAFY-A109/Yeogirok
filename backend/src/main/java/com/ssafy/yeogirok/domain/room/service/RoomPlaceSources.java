package com.ssafy.yeogirok.domain.room.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.course.repository.PublicCourseRepository;
import com.ssafy.yeogirok.domain.course.repository.ViewableCourse;
import com.ssafy.yeogirok.domain.course.service.CourseAuthors;
import com.ssafy.yeogirok.domain.room.dto.SourceRecordResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;

import lombok.RequiredArgsConstructor;

/**
 * 후보의 원본 공개 코스(sourceRecord, API-028·022). 공개 코스 상세(API-016)·가져오기(API-030)와 같은 기준
 * ({@link PublicCourseRepository#findViewable})으로 지금 볼 수 있는 코스만 돌려준다. 볼 수 없는 후보는 결과에 없다(응답의
 * sourceRecord 가 null). 후보 수와 관계없이 코스(기록+방)와 작성자를 한 번씩 읽는다. 호출한 쪽 트랜잭션 안에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class RoomPlaceSources {

	private final PublicCourseRepository publicCourseRepository;
	private final CourseAuthors courseAuthors;

	/** @return 원본 기록 ID → 지금 볼 수 있는 원본 코스 */
	public Map<Long, SourceRecordResponse> of(Collection<RoomPlace> places) {
		Set<Long> recordIds = places.stream().map(RoomPlace::getSourceRecordId).filter(Objects::nonNull)
			.collect(Collectors.toSet());
		if (recordIds.isEmpty()) {
			return Map.of();
		}
		List<ViewableCourse> courses = publicCourseRepository.findViewable(recordIds);
		Map<Long, List<PublicAuthor>> authors = courseAuthors.byRoom(courses.stream()
			.map(course -> course.room().getId()).toList());
		return courses.stream().collect(Collectors.toMap(course -> course.record().getId(),
			course -> new SourceRecordResponse(course.record().getId(), course.record().getTitle(),
				authors.getOrDefault(course.room().getId(), List.of()))));
	}
}
