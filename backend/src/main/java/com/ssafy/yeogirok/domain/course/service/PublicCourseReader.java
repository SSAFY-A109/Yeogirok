package com.ssafy.yeogirok.domain.course.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.course.dto.CourseDay;
import com.ssafy.yeogirok.domain.course.dto.PublicPhoto;
import com.ssafy.yeogirok.domain.course.dto.PublicPlace;
import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 공개 코스 조립(명세 {@code specs/course-publish-api.md} 4-1). 기록 원본을 그대로 읽는다. 방문·사진·장소·카테고리 이름을
 * 각각 한 번에 읽고, 이야기·영수증은 읽지 않는다. 호출한 쪽 트랜잭션 안에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class PublicCourseReader {

	private final ActualVisitRepository actualVisitRepository;
	private final VisitImageRepository visitImageRepository;
	private final PlaceRepository placeRepository;
	private final CategoryNames categoryNames;
	private final FileStorage fileStorage;
	private final FileProperties fileProperties;

	public CourseSnapshot snapshot(Long recordId) {
		List<ActualVisit> visits = actualVisitRepository.findByRecordIdOrderByVisitDateAscPositionAsc(recordId);
		Map<Long, List<VisitImage>> photosByVisit = visitImageRepository.findByRecordIdOrderByIdAsc(recordId).stream()
			.filter(image -> image.getVisitId() != null)
			.sorted(Comparator.comparing(VisitImage::getPosition))
			.collect(groupingBy(VisitImage::getVisitId));
		return new CourseSnapshot(visits, photosByVisit);
	}

	/**
	 * @param roomStartDate 방 시작일(없으면 null). 일차 계산에 쓴다
	 * @return 방문이 있는 날만, 일차 오름차순. 사진 URL 은 서명만 한다(S3 HEAD 없음, 기록 059 와 같음)
	 */
	public List<CourseDay> days(CourseSnapshot snapshot, LocalDate roomStartDate, PhotoScope scope) {
		List<ActualVisit> visits = snapshot.visits();
		if (visits.isEmpty()) {
			return List.of();
		}
		Map<Long, Place> places = placeRepository.findAllById(visits.stream().map(ActualVisit::getPlaceId).toList())
			.stream().collect(toMap(Place::getId, Function.identity()));
		Map<String, String> names = categoryNames.of(places.values().stream().map(Place::getCategoryCode).toList());
		LocalDate firstDay = firstDay(roomStartDate, visits.getFirst().getVisitDate());
		String bucket = fileProperties.bucketFor(FilePurpose.RECORD_PHOTO);

		Map<Integer, List<CourseDay.Item>> byDay = new LinkedHashMap<>();
		for (ActualVisit visit : visits) {
			List<PublicPhoto> photos = snapshot.photosOf(visit.getId()).stream()
				.filter(image -> scope == PhotoScope.LINKED || scope == PhotoScope.PUBLIC_SELECTED && image.isPublicSelected())
				.map(image -> new PublicPhoto(image.getId(),
					fileStorage.presignGet(bucket, image.getImagePath(), fileProperties.downloadUrlTtl()), null))
				.toList();
			int day = (int) ChronoUnit.DAYS.between(firstDay, visit.getVisitDate()) + 1;
			byDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(new CourseDay.Item(visit.getId(),
				PublicPlace.from(places.get(visit.getPlaceId()), names), visit.getPosition(), photos));
		}
		return byDay.entrySet().stream().map(entry -> new CourseDay(entry.getKey(), entry.getValue())).toList();
	}

	/** 여행 첫날: 방 시작일. 시작일이 없거나 첫 방문이 더 이르면 첫 방문 날짜(명세 5절 2, 일차가 1보다 작아지지 않게). */
	static LocalDate firstDay(LocalDate roomStartDate, LocalDate earliestVisitDate) {
		return roomStartDate == null || earliestVisitDate.isBefore(roomStartDate) ? earliestVisitDate : roomStartDate;
	}
}
