package com.ssafy.yeogirok.domain.course.service;

import static java.util.stream.Collectors.groupingBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.course.MainProperties;
import com.ssafy.yeogirok.domain.course.OfficialAccount;
import com.ssafy.yeogirok.domain.course.dto.PopularPlaceListResponse;
import com.ssafy.yeogirok.domain.course.repository.ImportedPlace;
import com.ssafy.yeogirok.domain.course.repository.ImportedPlaceRepository;
import com.ssafy.yeogirok.domain.course.seed.CourseSeedProperties;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.room.dto.PlaceInfo;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 메인 인기 장소(API-093, 명세 {@code specs/main-api.md} 3). 최근 30일 공개 코스에서 가져온 후보를 장소별로 묶어 서로 다른
 * 대상 방 수 내림차순(같으면 무작위)으로 최대 5개. 30일 후보 행을 읽어 서비스에서 묶는다(명세 5절 8).
 * 설정이 켜져 있으면 공식 코스 방도 "그 장소를 담은 여행방 1곳"으로 기간 없이 더한다(S15P31A109-231).
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(MainProperties.class)
public class PopularPlaceService {

	static final int MAX_ITEMS = 5;
	/** 표시값은 가장 최근 후보, 같은 시각이면 후보 ID 큰 것. */
	private static final Comparator<ImportedPlace> LATEST = Comparator.comparing(ImportedPlace::importedAt)
		.thenComparing(ImportedPlace::roomPlaceId);

	private final ImportedPlaceRepository importedPlaceRepository;
	private final CategoryNames categoryNames;
	private final MainProperties mainProperties;
	private final CourseSeedProperties seedProperties;
	private final Clock clock;

	/**
	 * 같은 장소: 외부 장소는 provider + providerPlaceId, 직접 입력은 이름·좌표·주소가 모두 같을 때(노션 093). 좌표는 소수 끝 0을
	 * 지워 비교한다(numeric(10,7) 값이라 저장된 그대로면 같다).
	 */
	private record PlaceKey(PlaceProvider provider, String providerPlaceId, String name, BigDecimal latitude,
		BigDecimal longitude, String address) {

		static PlaceKey of(Place place) {
			if (place.getProvider() != PlaceProvider.MANUAL) {
				return new PlaceKey(place.getProvider(), place.getProviderPlaceId(), null, null, null, null);
			}
			return new PlaceKey(PlaceProvider.MANUAL, null, place.getName(), place.getLatitude().stripTrailingZeros(),
				place.getLongitude().stripTrailingZeros(), place.getAddress());
		}
	}

	private record Ranked(PlaceKey key, Place display, long rooms) {
	}

	/** 없는 category C003. 결과가 없으면 빈 목록. */
	@Transactional(readOnly = true)
	public PopularPlaceListResponse popular(String category) {
		if (category != null && TravelCategory.fromCode(category).isEmpty()) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "category: 없는 카테고리입니다.");
		}
		ImportWindow window = ImportWindow.now(clock);
		List<ImportedPlace> rows = new ArrayList<>(
			importedPlaceRepository.findImported(category, window.start(), window.end()));
		if (mainProperties.includeOfficialCourses()) {
			rows.addAll(importedPlaceRepository.findOfficialCoursePlaces(category, OfficialAccount.PROVIDER,
				OfficialAccount.SUBJECT, seedProperties.ownerUserId()));
		}
		List<Ranked> places = new ArrayList<>(rows.stream()
			.collect(groupingBy(imported -> PlaceKey.of(imported.place())))
			.entrySet().stream()
			.map(group -> new Ranked(group.getKey(), group.getValue().stream().max(LATEST).orElseThrow().place(),
				group.getValue().stream().map(ImportedPlace::roomId).distinct().count()))
			.toList());
		// 같은 방 수끼리는 무작위: 섞은 뒤 방 수로만 정렬한다(정렬은 안정적이라 섞인 순서가 남는다).
		Collections.shuffle(places, ThreadLocalRandom.current());
		List<Ranked> top = places.stream()
			.sorted(Comparator.comparingLong(Ranked::rooms).reversed())
			.limit(MAX_ITEMS)
			.toList();
		Map<String, String> names = categoryNames.of(top.stream().map(ranked -> ranked.display().getCategoryCode())
			.toList());
		return new PopularPlaceListResponse(IntStream.range(0, top.size())
			.mapToObj(i -> new PopularPlaceListResponse.Item(i + 1, PlaceInfo.from(top.get(i).display(), names),
				top.get(i).rooms()))
			.toList(), window.basis());
	}
}
