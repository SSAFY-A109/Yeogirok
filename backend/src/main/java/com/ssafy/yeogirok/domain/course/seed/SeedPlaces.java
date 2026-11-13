package com.ssafy.yeogirok.domain.course.seed;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;
import com.ssafy.yeogirok.domain.place.tour.TourCategoryMapper;

import lombok.RequiredArgsConstructor;

/**
 * 투어API 장소를 찾거나 저장한다(명세 6-4). 이미 있으면 값을 고치지 않는다.
 * TODO(S15P31A109-219): RoomPlaceAddService.findOrCreate 와 합친다. 여기서는 법정동을 카카오로 조회하지 않고 묶음 파일 값을 쓰며,
 * 적재는 Redis 잠금으로 한 번에 하나만 돌아 같은 장소 동시 저장 경쟁이 없다.
 */
@Component
@RequiredArgsConstructor
class SeedPlaces {

	static final String ATTRIBUTION = "한국관광공사";

	private final PlaceRepository placeRepository;
	private final Clock clock;

	/** 같은 트랜잭션 안에서 부른다. */
	void ensure(SeedBundle.Place place) {
		if (placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.TOUR_API, place.contentId()).isPresent()) {
			return;
		}
		TravelCategory category = TourCategoryMapper.categoryOf(place.lclsSystm2(), place.lclsSystm3());
		boolean hasImage = place.imageUrl() != null && !place.imageUrl().isBlank();
		placeRepository.save(Place.tourApi(place.contentId(), place.name(), place.mapy(), place.mapx(), place.address(),
			place.regionCode(), place.regionName(), category == null ? null : category.name(),
			hasImage ? place.imageUrl() : null, hasImage ? ATTRIBUTION : null, clock.instant()));
	}
}
