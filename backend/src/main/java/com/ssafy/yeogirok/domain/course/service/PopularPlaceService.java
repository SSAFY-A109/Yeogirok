package com.ssafy.yeogirok.domain.course.service;

import static java.util.stream.Collectors.groupingBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.CategoryNames;
import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.course.dto.PopularPlaceListResponse;
import com.ssafy.yeogirok.domain.course.repository.ImportedPlace;
import com.ssafy.yeogirok.domain.course.repository.ImportedPlaceRepository;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.room.dto.PlaceInfo;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 메인 인기 장소(API-093, 명세 {@code specs/main-api.md} 3). 최근 30일 공개 코스에서 가져온 후보를 장소별로 묶어 서로 다른
 * 대상 방 수 내림차순(같으면 장소 식별값 오름차순)으로 최대 5개. 30일 후보 행을 읽어 서비스에서 묶는다(명세 5절 8).
 */
@Service
@RequiredArgsConstructor
public class PopularPlaceService {

	static final int MAX_ITEMS = 5;
	/** 장소 식별값 오름차순(명세 5절 9). null 이 먼저다. */
	private static final Comparator<PlaceKey> KEY_ORDER = Comparator
		.comparing(PlaceKey::provider)
		.thenComparing(PlaceKey::providerPlaceId, Comparator.nullsFirst(Comparator.naturalOrder()))
		.thenComparing(PlaceKey::name, Comparator.nullsFirst(Comparator.naturalOrder()))
		.thenComparing(PlaceKey::latitude, Comparator.nullsFirst(Comparator.naturalOrder()))
		.thenComparing(PlaceKey::longitude, Comparator.nullsFirst(Comparator.naturalOrder()))
		.thenComparing(PlaceKey::address, Comparator.nullsFirst(Comparator.naturalOrder()));
	/** 표시값은 가장 최근 후보, 같은 시각이면 후보 ID 큰 것. */
	private static final Comparator<ImportedPlace> LATEST = Comparator.comparing(ImportedPlace::importedAt)
		.thenComparing(ImportedPlace::roomPlaceId);

	private final ImportedPlaceRepository importedPlaceRepository;
	private final CategoryNames categoryNames;
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
		List<Ranked> top = importedPlaceRepository.findImported(category, window.start(), window.end()).stream()
			.collect(groupingBy(imported -> PlaceKey.of(imported.place())))
			.entrySet().stream()
			.map(group -> new Ranked(group.getKey(), group.getValue().stream().max(LATEST).orElseThrow().place(),
				group.getValue().stream().map(ImportedPlace::roomId).distinct().count()))
			.sorted(Comparator.comparingLong(Ranked::rooms).reversed().thenComparing(Ranked::key, KEY_ORDER))
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
