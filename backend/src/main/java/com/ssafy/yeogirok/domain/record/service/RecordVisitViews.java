package com.ssafy.yeogirok.domain.record.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.dto.ActualVisitView;
import com.ssafy.yeogirok.domain.record.dto.RecordPhotoView;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.entity.VisitStory;
import com.ssafy.yeogirok.domain.record.repository.ActiveUserRepository;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitStoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 기록의 실제 방문·사진 보관함 응답 조립(API-059·067 이 같은 구조를 쓴다). 방문·사진·이야기·장소·회원 상태를 각각 한 번에 읽는다.
 * 탈퇴했거나 없는 회원의 uploadedBy·authorId 는 null 이다(노션 059).
 */
@Component
@RequiredArgsConstructor
public class RecordVisitViews {

	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;
	private final VisitStoryRepository visitStoryRepository;
	private final PlaceRepository placeRepository;
	private final ActiveUserRepository activeUserRepository;

	/** 호출한 쪽 트랜잭션 안에서 부른다. */
	public Board load(Long recordId) {
		List<ActualVisit> visits = actualVisitRepository.findByRecordIdOrderByVisitDateAscPositionAsc(recordId);
		List<VisitImage> images = visitImageRepository.findByRecordIdOrderByIdAsc(recordId);
		List<VisitStory> stories = visits.isEmpty() ? List.of()
			: visitStoryRepository.findByVisitIdInOrderByIdAsc(visits.stream().map(ActualVisit::getId).toList());
		Map<Long, Place> places = placeRepository.findAllById(visits.stream().map(ActualVisit::getPlaceId).toList())
			.stream().collect(toMap(Place::getId, Function.identity()));
		Function<Long, Long> shown = shownUserIds(images, stories);

		List<RecordPhotoView> photoPool = images.stream().map(image -> photo(image, shown)).toList();
		Map<Long, List<RecordPhotoView>> photosByVisit = images.stream()
			.filter(image -> image.getVisitId() != null)
			.sorted(Comparator.comparing(VisitImage::getPosition))
			.collect(groupingBy(VisitImage::getVisitId, mapping(image -> photo(image, shown), toList())));
		Map<Long, List<ActualVisitView.Story>> storiesByVisit = stories.stream()
			.collect(groupingBy(VisitStory::getVisitId,
				mapping(story -> new ActualVisitView.Story(story.getId(), shown.apply(story.getAuthorId()),
					story.getBody()), toList())));

		List<ActualVisitView> views = visits.stream().map(visit -> new ActualVisitView(visit.getId(),
			RecordPlaceView.from(places.get(visit.getPlaceId())), visit.getPlannedItemId(), visit.getVisitDate(),
			visit.getVisitedAt(), visit.getPosition(), photosByVisit.getOrDefault(visit.getId(), List.of()),
			storiesByVisit.getOrDefault(visit.getId(), List.of()))).toList();
		return new Board(views, photoPool);
	}

	private Function<Long, Long> shownUserIds(List<VisitImage> images, List<VisitStory> stories) {
		Set<Long> userIds = new HashSet<>();
		Stream.concat(images.stream().map(VisitImage::getUploadedBy), stories.stream().map(VisitStory::getAuthorId))
			.forEach(userIds::add);
		Set<Long> active = userIds.isEmpty() ? Set.of() : new HashSet<>(activeUserRepository.findActiveIds(userIds));
		return userId -> active.contains(userId) ? userId : null;
	}

	private static RecordPhotoView photo(VisitImage image, Function<Long, Long> shown) {
		return new RecordPhotoView(image.getId(), null, shown.apply(image.getUploadedBy()), image.getVisitId());
	}


	/** 실제 방문(날짜·순서대로)과 기록 전체 사진(올린 순서). */
	public record Board(List<ActualVisitView> visits, List<RecordPhotoView> photoPool) {
	}
}
