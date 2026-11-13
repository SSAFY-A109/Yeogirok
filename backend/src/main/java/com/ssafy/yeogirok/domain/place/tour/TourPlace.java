package com.ssafy.yeogirok.domain.place.tour;

import java.math.BigDecimal;

import com.ssafy.yeogirok.domain.category.TravelCategory;

/**
 * TourAPI 관광정보 한 건. 좌표가 없는 항목은 만들지 않는다(추천·담기에 쓸 수 없다).
 *
 * @param contentId  콘텐츠 ID. 우리 providerPlaceId 다
 * @param address    addr1(+ addr2). 없으면 null
 * @param imageUrl   대표 이미지(firstimage). 없으면 null
 * @param lclsSystm1 분류 대분류. 없으면 null
 * @param lclsSystm2 분류 중분류. 없으면 null
 * @param lclsSystm3 분류 소분류. 없으면 null
 */
public record TourPlace(
	String contentId,
	String title,
	String address,
	BigDecimal latitude,
	BigDecimal longitude,
	String imageUrl,
	String lclsSystm1,
	String lclsSystm2,
	String lclsSystm3
) {

	/** TourAPI 사진의 출처 표기. */
	public static final String IMAGE_ATTRIBUTION = "한국관광공사";

	/** 확정 매핑({@link TourCategoryMapper})의 공통 카테고리. 카테고리 없음이면 null. */
	public TravelCategory category() {
		return TourCategoryMapper.categoryOf(lclsSystm2, lclsSystm3);
	}

	/** {@link #category()} 의 코드. 카테고리 없음이면 null. */
	public String categoryCode() {
		TravelCategory category = category();
		return category == null ? null : category.name();
	}

	/** 사진이 있으면 {@link #IMAGE_ATTRIBUTION}, 없으면 null. */
	public String imageAttribution() {
		return imageUrl == null ? null : IMAGE_ATTRIBUTION;
	}
}
