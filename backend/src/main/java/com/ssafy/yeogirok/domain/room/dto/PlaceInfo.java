package com.ssafy.yeogirok.domain.room.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.ssafy.yeogirok.domain.room.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;

/**
 * 후보에 저장한 장소 정보(명세의 Place). 표시명은 확인한 값만 주고 지어내지 않는다.
 * <ul>
 *   <li>categoryName: category_code 에 대응하는 travel_categories.name. 코드가 없거나 그 행이 없으면 null.</li>
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

	/** @param categoryNames 카테고리 코드 → 표시명({@link com.ssafy.yeogirok.domain.category.CategoryNames}) */
	public static PlaceInfo from(RoomPlace place, Map<String, String> categoryNames) {
		String categoryName = place.getCategoryCode() == null ? null : categoryNames.get(place.getCategoryCode());
		return new PlaceInfo(place.getProvider(), place.getProviderPlaceId(), place.getPlaceName(),
			place.getLatitude(), place.getLongitude(), place.getAddress(), place.getCategoryCode(), categoryName,
			place.getRegionCode(), place.getRegionName(), place.getImageUrl(), place.getImageAttribution());
	}
}
