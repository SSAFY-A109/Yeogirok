package com.ssafy.yeogirok.domain.record.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.dto.ActualVisitView;
import com.ssafy.yeogirok.domain.record.dto.RecordPhotoView;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.entity.VisitStory;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitStoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 기록의 실제 방문·사진 보관함 응답 조립(API-059·067 이 같은 구조를 쓴다). 방문·사진·이야기·장소·회원 상태를 각각 한 번에 읽는다.
 * 탈퇴했거나 없는 회원의 uploadedBy·authorId 는 null 이다(노션 059).
 *
 * <p>사진 URL 은 부르는 쪽이 기록 참여 권한을 확인한 뒤 사진마다 서명만 해서 만든다. S3 HEAD 는 하지 않는다(사진 수만큼
 * 외부 호출이 늘어나서다. 팁 048 과 같음). 지워진 객체면 앱이 URL 을 열 때 실패한다.
 */
@Component
@RequiredArgsConstructor
public class RecordVisitViews {

	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;
	private final VisitStoryRepository visitStoryRepository;
	private final PlaceRepository placeRepository;
	private final RecordAuthors recordAuthors;
	private final FileStorage fileStorage;
	private final FileProperties fileProperties;

	/** 호출한 쪽 트랜잭션 안에서 부른다. */
	public Board load(Long recordId) {
		List<ActualVisit> visits = actualVisitRepository.findByRecordIdOrderByVisitDateAscPositionAsc(recordId);
		List<VisitImage> images = visitImageRepository.findByRecordIdOrderByIdAsc(recordId);
		List<VisitStory> stories = visits.isEmpty() ? List.of()
			: visitStoryRepository.findByVisitIdInOrderByIdAsc(visits.stream().map(ActualVisit::getId).toList());
		Map<Long, Place> places = placeRepository.findAllById(visits.stream().map(ActualVisit::getPlaceId).toList())
			.stream().collect(toMap(Place::getId, Function.identity()));
		UnaryOperator<Long> shown = recordAuthors.shownIds(Stream.concat(
			images.stream().map(VisitImage::getUploadedBy), stories.stream().map(VisitStory::getAuthorId)).toList());
		String bucket = fileProperties.bucketFor(FilePurpose.RECORD_PHOTO);
		Map<Long, RecordPhotoView> photos = images.stream().collect(toMap(VisitImage::getId, image -> new RecordPhotoView(
			image.getId(), fileStorage.presignGet(bucket, image.getImagePath(), fileProperties.downloadUrlTtl()), null,
			shown.apply(image.getUploadedBy()), image.getVisitId())));

		List<RecordPhotoView> photoPool = images.stream().map(image -> photos.get(image.getId())).toList();
		Map<Long, List<RecordPhotoView>> photosByVisit = images.stream()
			.filter(image -> image.getVisitId() != null)
			.sorted(Comparator.comparing(VisitImage::getPosition))
			.collect(groupingBy(VisitImage::getVisitId, mapping(image -> photos.get(image.getId()), toList())));
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



	/** 실제 방문(날짜·순서대로)과 기록 전체 사진(올린 순서). */
	public record Board(List<ActualVisitView> visits, List<RecordPhotoView> photoPool) {
	}
}
