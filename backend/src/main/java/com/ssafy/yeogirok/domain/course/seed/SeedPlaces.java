package com.ssafy.yeogirok.domain.course.seed;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.service.TourPlaces;
import com.ssafy.yeogirok.domain.place.tour.TourCategoryMapper;

import lombok.RequiredArgsConstructor;

/**
 * 공식 코스의 투어API 장소를 찾거나 저장한다(명세 6-4·10절). 찾기·처음 저장은 029 추천 담기와 같은 {@link TourPlaces} 로 하고,
 * 법정동은 카카오로 조회하지 않고 묶음 파일 값(222 스크립트에서 정함)을 쓴다. 이미 있으면 값을 고치지 않는다.
 */
@Component
@RequiredArgsConstructor
class SeedPlaces {

	static final String ATTRIBUTION = "한국관광공사";

	private final TourPlaces tourPlaces;
	private final Clock clock;

	/** 코스 트랜잭션 안에서 불러도 된다(TourPlaces 가 새 트랜잭션으로 저장). */
	void ensure(SeedBundle.Place place) {
		tourPlaces.findOrCreate(place.contentId(), () -> {
			TravelCategory category = TourCategoryMapper.categoryOf(place.lclsSystm2(), place.lclsSystm3());
			boolean hasImage = place.imageUrl() != null && !place.imageUrl().isBlank();
			return Place.tourApi(place.contentId(), place.name(), place.mapy(), place.mapx(), place.address(),
				place.regionCode(), place.regionName(), category == null ? null : category.name(),
				hasImage ? place.imageUrl() : null, hasImage ? ATTRIBUTION : null, clock.instant());
		});
	}
}
