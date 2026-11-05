package com.ssafy.yeogirok.domain.room.dto;

import java.math.BigDecimal;

import com.ssafy.yeogirok.domain.room.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;

/**
 * 후보에 저장한 장소 정보(명세의 Place). 표시명은 확인한 값만 주고 지어내지 않는다.
 * <ul>
 *   <li>categoryName: travel_categories.name 을 쓰기로 했지만 아직 이름 기준 데이터가 없어 null 이다.
 *       TODO API-029·030 에서 카테고리가 있는 후보를 담을 때 이름 기준을 함께 만든다.</li>
 *   <li>regionName: 담을 때 저장한 room_places.region_name 을 그대로 준다.</li>
 * </ul>
 */
public record PlaceInfo(
	PlaceProvider provider,
	String providerPlaceId,
	String name,
	BigDecimal latitude,
	BigDecimal longitude,
	String address,
	String categoryCode,
	String categoryName,
	String regionCode,
	String regionName,
	String imageUrl,
	String imageAttribution
) {

	public static PlaceInfo from(RoomPlace place) {
		return new PlaceInfo(place.getProvider(), place.getProviderPlaceId(), place.getPlaceName(),
			place.getLatitude(), place.getLongitude(), place.getAddress(), place.getCategoryCode(), null,
			place.getRegionCode(), place.getRegionName(), place.getImageUrl(), place.getImageAttribution());
	}
}
