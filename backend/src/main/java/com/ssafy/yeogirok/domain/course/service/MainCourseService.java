package com.ssafy.yeogirok.domain.course.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchCondition;
import com.ssafy.yeogirok.domain.course.dto.MainCourseListResponse;
import com.ssafy.yeogirok.domain.course.repository.CourseCandidate;
import com.ssafy.yeogirok.domain.course.repository.PublicCourseRepository;
import com.ssafy.yeogirok.domain.course.repository.PublicPhotoDate;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 메인 추천 코스(API-015, 명세 {@code specs/main-api.md} 2). 게시 중인 코스 중 여행 첫날~마지막 날 모든 날짜에 공개 사진이 있는
 * 코스를 최근 30일 가져간 방 수 내림차순(같으면 id 내림차순)으로 준다. 후보·가져간 방 수는 014 와 같은 쿼리, 카드 재료는
 * {@link CourseCards} 로 읽는다. 014 처럼 후보를 한 번에 읽어 노출 조건·정렬·커서를 서비스에서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class MainCourseService {

	static final int PREVIEW_PLACE_SIZE = 3;
	private static final Comparator<Ranked> POPULAR = Comparator.comparingLong((Ranked ranked) -> ranked.importCount())
		.thenComparingLong(ranked -> ranked.candidate().id()).reversed();

	private final PublicCourseRepository publicCourseRepository;
	private final CourseCards courseCards;
	private final PublicCourseReader publicCourseReader;
	private final CategoryNames categoryNames;
	private final Clock clock;

	private record Ranked(CourseCandidate candidate, List<LocalDate> dates, long importCount) {
	}

	/** 없는 category CO004, regionCode 형식 C003, 커서 C006, limit 20·50(C007). */
	@Transactional(readOnly = true)
	public MainCourseListResponse recommend(String category, String regionCode, String cursor, Integer limit) {
		if (category != null && TravelCategory.fromCode(category).isEmpty()) {
			throw new BusinessException(ExceptionType.COURSE_RECOMMENDATION_CATEGORY_NOT_SUPPORTED);
		}
		if (regionCode != null && !CourseSearchCondition.isRegionCode(regionCode)) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"regionCode: 시·도 2자리 또는 시·군·구 5자리 숫자여야 합니다.");
		}
		int size = PageSize.resolve(limit);
		MainCourseCursor after = MainCourseCursor.decodeOrNull(cursor, clock.instant());
		ImportWindow window = after == null ? ImportWindow.now(clock) : ImportWindow.endingAt(after.windowEnd());

		List<Ranked> eligible = eligible(publicCourseRepository.findCandidates(null,
			regionCode == null ? null : regionCode + "%", category, null, window.start(), window.end()));
		List<Ranked> rest = after == null ? eligible : eligible.stream()
			.filter(ranked -> after.precedes(ranked.importCount(), ranked.candidate().id()))
			.toList();
		List<Ranked> page = rest.size() > size ? rest.subList(0, size) : rest;
		String nextCursor = rest.size() > size ? new MainCourseCursor(window.end(), page.getLast().importCount(),
			page.getLast().candidate().id()).encode() : null;
		return new MainCourseListResponse(items(page), nextCursor, window.basis());
	}

	/** 여행 첫날~마지막 날 모든 날짜에 공개 사진이 연결된 방문이 있는 코스만, 정렬해서(명세 2 노출 대상 2). */
	private List<Ranked> eligible(List<CourseCandidate> candidates) {
		if (candidates.isEmpty()) {
			return List.of();
		}
		Map<Long, Set<LocalDate>> photoDates = publicCourseRepository
			.findPublicPhotoDates(candidates.stream().map(CourseCandidate::id).toList()).stream()
			.collect(groupingBy(PublicPhotoDate::recordId, mapping(PublicPhotoDate::visitDate, toSet())));
		return candidates.stream()
			.map(candidate -> new Ranked(candidate, CoursePeriod.dates(candidate.roomStart(), candidate.roomEnd(),
				candidate.firstVisit(), candidate.lastVisit()), candidate.importCount()))
			.filter(ranked -> !ranked.dates().isEmpty()
				&& photoDates.getOrDefault(ranked.candidate().id(), Set.of()).containsAll(ranked.dates()))
			.sorted(POPULAR)
			.toList();
	}

	private List<MainCourseListResponse.Item> items(List<Ranked> page) {
		CourseCards.Sources sources = courseCards.load(page.stream().map(Ranked::candidate).toList());
		Map<String, String> names = categoryNames.of(sources.places().values().stream()
			.map(Place::getCategoryCode).toList());
		return page.stream().map(ranked -> {
			CourseCandidate course = ranked.candidate();
			List<ActualVisit> visits = sources.visitsOf(course.id());
			LocalDate firstDay = ranked.dates().getFirst();
			return new MainCourseListResponse.Item(course.id(), course.title(), sources.authorsOf(course.roomId()),
				CourseRegions.nameOf(visits.isEmpty() ? null : sources.placeOf(visits.getFirst())),
				sources.firstPhoto(course.id()).map(publicCourseReader::photoUrl).orElse(null), visits.size(),
				ranked.dates().size(), ranked.dates().size() - 1, course.transportMode(), ranked.importCount(),
				previewPlaces(sources, visits, firstDay, names));
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
