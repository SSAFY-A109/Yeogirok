package com.ssafy.yeogirok.domain.course.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.course.dto.CourseSearchCondition;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchResponse;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.course.repository.CourseCandidate;
import com.ssafy.yeogirok.domain.course.repository.PublicCourseRepository;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.global.request.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 공개된 코스 검색(API-014, 명세 {@code specs/course-search-api.md} 2). 조건에 맞는 공개 코스를 한 번에 읽어 기간 필터·정렬
 * (가져간 방 수 내림차순, 같으면 id 내림차순)·페이지를 정한다. 기간 계산을 상세(016)와 같은 {@link CoursePeriod} 한 곳에 두려고
 * DB 날짜 연산을 쓰지 않는다(공개 코스 수가 많아지면 쿼리로 옮긴다). 페이지 항목의 방문·장소·사진·작성자는 한 번씩 읽는다.
 */
@Service
@RequiredArgsConstructor
public class CourseSearchService {

	static final int ROUTE_PREVIEW_SIZE = 5;
	private static final Comparator<Ranked> POPULAR = Comparator.comparingLong((Ranked ranked) -> ranked.importCount())
		.thenComparingLong(ranked -> ranked.candidate().id()).reversed();

	private final PublicCourseRepository publicCourseRepository;
	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;
	private final PlaceRepository placeRepository;
	private final PublicCourseReader publicCourseReader;
	private final CourseAuthors courseAuthors;

	private record Ranked(CourseCandidate candidate, CoursePeriod period, long importCount) {
	}

	/** limit 기본 20·최대 50(C007), 커서 C006. 조건 값 오류는 {@link CourseSearchCondition#of} 가 낸다. */
	@Transactional(readOnly = true)
	public CourseSearchResponse search(CourseSearchCondition condition, String cursor, Integer limit) {
		int size = PageSize.resolve(limit);
		CourseSearchCursor after = CourseSearchCursor.decodeOrNull(cursor);
		List<Ranked> matched = publicCourseRepository.findCandidates(condition.pattern(), condition.regionPrefix(),
				condition.category(), condition.transportMode(), ImportWindow.ALL.start(), ImportWindow.ALL.end()).stream()
			.map(candidate -> new Ranked(candidate, CoursePeriod.of(candidate.roomStart(), candidate.roomEnd(),
				candidate.firstVisit(), candidate.lastVisit()), candidate.importCount()))
			.filter(ranked -> condition.duration().matches(ranked.period().dayCount()))
			.sorted(POPULAR)
			.toList();
		List<Ranked> rest = after == null ? matched : matched.stream()
			.filter(ranked -> after.precedes(ranked.importCount(), ranked.candidate().id()))
			.toList();
		List<Ranked> page = rest.size() > size ? rest.subList(0, size) : rest;
		String nextCursor = rest.size() > size
			? new CourseSearchCursor(page.getLast().importCount(), page.getLast().candidate().id()).encode() : null;
		return new CourseSearchResponse(items(page), matched.size(), nextCursor);
	}

	private List<CourseSearchResponse.Item> items(List<Ranked> page) {
		if (page.isEmpty()) {
			return List.of();
		}
		List<Long> ids = page.stream().map(ranked -> ranked.candidate().id()).toList();
		Map<Long, List<ActualVisit>> visits = actualVisitRepository
			.findByRecordIdInOrderByRecordIdAscVisitDateAscPositionAsc(ids).stream()
			.collect(groupingBy(ActualVisit::getRecordId));
		Map<Long, Place> places = placeRepository.findAllById(visits.values().stream().flatMap(List::stream)
				.map(ActualVisit::getPlaceId).distinct().toList()).stream()
			.collect(toMap(Place::getId, Function.identity()));
		Map<Long, List<VisitImage>> photos = visitImageRepository
			.findByRecordIdInAndPublicSelectedTrueAndVisitIdIsNotNull(ids).stream()
			.collect(groupingBy(VisitImage::getVisitId));
		Map<Long, List<PublicAuthor>> authors = courseAuthors.byRoom(page.stream()
			.map(ranked -> ranked.candidate().roomId()).toList());
		return page.stream().map(ranked -> {
			CourseCandidate course = ranked.candidate();
			List<ActualVisit> courseVisits = visits.getOrDefault(course.id(), List.of());
			Place first = courseVisits.isEmpty() ? null : places.get(courseVisits.getFirst().getPlaceId());
			return new CourseSearchResponse.Item(course.id(), course.title(),
				authors.getOrDefault(course.roomId(), List.of()), CourseRegions.nameOf(first),
				thumbnail(courseVisits, photos, first), courseVisits.size(), ranked.period().dayCount(),
				ranked.period().nightCount(), course.transportMode(),
				courseVisits.stream().limit(ROUTE_PREVIEW_SIZE).map(visit -> places.get(visit.getPlaceId()))
					.filter(place -> place != null).map(Place::getName).toList(),
				ranked.importCount());
		}).toList();
	}

	/** 방문 순서대로 첫 공개 사진, 없으면 첫 방문 장소 이미지(프론트 016 화면과 같은 규칙). */
	private String thumbnail(List<ActualVisit> visits, Map<Long, List<VisitImage>> photos, Place first) {
		return visits.stream()
			.flatMap(visit -> photos.getOrDefault(visit.getId(), List.of()).stream()
				.sorted(Comparator.comparing(VisitImage::getPosition)))
			.findFirst()
			.map(publicCourseReader::photoUrl)
			.orElse(first == null ? null : first.getImageUrl());
	}
}
