package com.ssafy.yeogirok.domain.course.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.course.repository.CourseCandidate;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 공개 코스 카드 목록(API-014 검색·015 메인 추천)의 재료를 페이지 단위로 한 번씩 읽는다: 방문(날짜·순서대로), 장소, 공개
 * 선택된 연결 사진(순서·ID 대로), 작성자. 호출한 쪽 트랜잭션 안에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class CourseCards {

	private static final Comparator<VisitImage> PHOTO_ORDER = Comparator.comparing(VisitImage::getPosition)
		.thenComparing(VisitImage::getId);

	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;
	private final PlaceRepository placeRepository;
	private final CourseAuthors courseAuthors;

	public Sources load(Collection<CourseCandidate> courses) {
		if (courses.isEmpty()) {
			return new Sources(Map.of(), Map.of(), Map.of(), Map.of());
		}
		List<Long> ids = courses.stream().map(CourseCandidate::id).toList();
		Map<Long, List<ActualVisit>> visits = actualVisitRepository
			.findByRecordIdInOrderByRecordIdAscVisitDateAscPositionAsc(ids).stream()
			.collect(groupingBy(ActualVisit::getRecordId));
		Map<Long, Place> places = placeRepository.findAllById(visits.values().stream().flatMap(List::stream)
				.map(ActualVisit::getPlaceId).distinct().toList()).stream()
			.collect(toMap(Place::getId, Function.identity()));
		Map<Long, List<VisitImage>> photos = visitImageRepository
			.findByRecordIdInAndPublicSelectedTrueAndVisitIdIsNotNull(ids).stream()
			.sorted(PHOTO_ORDER)
			.collect(groupingBy(VisitImage::getVisitId));
		return new Sources(visits, places, photos,
			courseAuthors.byRoom(courses.stream().map(CourseCandidate::roomId).toList()));
	}

	public record Sources(Map<Long, List<ActualVisit>> visits, Map<Long, Place> places,
		Map<Long, List<VisitImage>> photos, Map<Long, List<PublicAuthor>> authors) {

		public List<ActualVisit> visitsOf(Long recordId) {
			return visits.getOrDefault(recordId, List.of());
		}

		/** @return 장소 행이 없으면 null */
		public Place placeOf(ActualVisit visit) {
			return places.get(visit.getPlaceId());
		}

		/** 공개 선택된 연결 사진(순서·ID 대로). */
		public List<VisitImage> photosOf(ActualVisit visit) {
			return photos.getOrDefault(visit.getId(), List.of());
		}

		public List<PublicAuthor> authorsOf(Long roomId) {
			return authors.getOrDefault(roomId, List.of());
		}

		/** 방문 순서대로 첫 공개 사진. */
		public Optional<VisitImage> firstPhoto(Long recordId) {
			return visitsOf(recordId).stream().flatMap(visit -> photosOf(visit).stream()).findFirst();
		}
	}
}
