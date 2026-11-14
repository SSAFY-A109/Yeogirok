package com.ssafy.yeogirok.domain.course.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchCondition;
import com.ssafy.yeogirok.domain.course.dto.MainCourseListResponse;
import com.ssafy.yeogirok.domain.course.repository.CourseCandidate;
import com.ssafy.yeogirok.domain.course.repository.CoursePhotoStats;
import com.ssafy.yeogirok.domain.course.repository.PublicCourseRepository;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 메인 추천 코스(API-015, 명세 {@code specs/main-api.md} 2·227). 게시 중인 코스 중 여행 첫날~마지막 날 모든 날짜에 공개 사진이
 * 있는 코스에서 최대 8개를 요청마다 무작위로 고른다({@link MainCoursePicker}). 다음 쪽은 없고, 카드 재료는 고른 코스만
 * {@link CourseCards} 로 읽는다. 후보는 요청마다 전부 읽는다(공식 코스 수백 개 규모, 수천 개가 되면 쿼리로 줄인다).
 */
@Service
@RequiredArgsConstructor
public class MainCourseService {

	static final int PREVIEW_PLACE_SIZE = 3;
	/** IN 절 바인드 수 한도(PostgreSQL 32767)를 넘지 않게 나눠 센다. */
	static final int STATS_CHUNK = 1000;

	private final PublicCourseRepository publicCourseRepository;
	private final CourseCards courseCards;
	private final PublicCourseReader publicCourseReader;
	private final CategoryNames categoryNames;
	private final Clock clock;

	/** 없는 category CO004, regionCode 형식 C003. 다음 쪽이 없어 nextCursor 는 늘 null(옛 앱의 cursor·limit 는 받지 않는다). */
	@Transactional(readOnly = true)
	public MainCourseListResponse recommend(String category, String regionCode) {
		if (category != null && TravelCategory.fromCode(category).isEmpty()) {
			throw new BusinessException(ExceptionType.COURSE_RECOMMENDATION_CATEGORY_NOT_SUPPORTED);
		}
		String regionPrefix = CourseSearchCondition.regionPrefix(regionCode, ExceptionType.NOT_ALLOWED_VALUE);
		ImportWindow window = ImportWindow.now(clock);
		List<CourseCandidate> candidates = publicCourseRepository.findMainCourses(regionPrefix, category,
			window.start(), window.end());
		List<CourseCandidate> picked = MainCoursePicker.pick(entries(candidates), ThreadLocalRandom.current());
		return new MainCourseListResponse(items(picked), null, window.basis());
	}

	private List<MainCoursePicker.Entry> entries(List<CourseCandidate> candidates) {
		Map<Long, CoursePhotoStats> stats = new HashMap<>();
		List<Long> ids = candidates.stream().map(CourseCandidate::id).toList();
		for (int from = 0; from < ids.size(); from += STATS_CHUNK) {
			publicCourseRepository.countPhotos(ids.subList(from, Math.min(from + STATS_CHUNK, ids.size())))
				.forEach(stat -> stats.put(stat.recordId(), stat));
		}
		return candidates.stream().map(course -> {
			CoursePhotoStats stat = stats.get(course.id());
			int days = CoursePeriod.dates(course.roomStart(), course.roomEnd(), course.firstVisit(), course.lastVisit())
				.size();
			return new MainCoursePicker.Entry(course, stat.visits(), stat.photoVisits(), stat.publicPhotos(), days);
		}).toList();
	}

	private List<MainCourseListResponse.Item> items(List<CourseCandidate> page) {
		CourseCards.Sources sources = courseCards.load(page);
		Map<String, String> names = categoryNames.of(sources.places().values().stream()
			.map(Place::getCategoryCode).toList());
		return page.stream().map(course -> {
			List<ActualVisit> visits = sources.visitsOf(course.id());
			List<LocalDate> dates = CoursePeriod.dates(course.roomStart(), course.roomEnd(), course.firstVisit(),
				course.lastVisit());
			return new MainCourseListResponse.Item(course.id(), course.title(), sources.authorsOf(course.roomId()),
				CourseRegions.nameOf(visits.isEmpty() ? null : sources.placeOf(visits.getFirst())),
				sources.firstPhoto(course.id()).map(publicCourseReader::photoUrl).orElse(null), visits.size(),
				dates.size(), dates.size() - 1, course.transportMode(), course.importCount(),
				previewPlaces(sources, visits, dates.getFirst(), names));
		}).toList();
	}

	/** 공개 사진이 있는 방문 중 날짜별 첫 방문을 일차 순으로 최대 3개. 방문은 날짜·순서, 사진은 순서·ID 대로(노션 015). */
	private List<MainCourseListResponse.PreviewPlace> previewPlaces(CourseCards.Sources sources,
		List<ActualVisit> visits, LocalDate firstDay, Map<String, String> names) {
		Map<LocalDate, ActualVisit> firstByDate = new LinkedHashMap<>();
		visits.stream().filter(visit -> !sources.photosOf(visit).isEmpty())
			.forEach(visit -> firstByDate.putIfAbsent(visit.getVisitDate(), visit));
		List<MainCourseListResponse.PreviewPlace> previews = new ArrayList<>();
		for (ActualVisit visit : firstByDate.values()) {
			if (previews.size() == PREVIEW_PLACE_SIZE) {
				break;
			}
			Place place = sources.placeOf(visit);
			VisitImage photo = sources.photosOf(visit).getFirst();
			String code = place == null ? null : place.getCategoryCode();
			previews.add(new MainCourseListResponse.PreviewPlace(visit.getId(),
				(int) ChronoUnit.DAYS.between(firstDay, visit.getVisitDate()) + 1, place == null ? null : place.getName(),
				code, code == null ? null : names.get(code), publicCourseReader.photoUrl(photo)));
		}
		return previews;
	}
}
