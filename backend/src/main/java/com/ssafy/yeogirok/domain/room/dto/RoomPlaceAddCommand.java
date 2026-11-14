package com.ssafy.yeogirok.domain.room.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;

/**
 * API-029 검사를 끝낸 요청. 멱등키 요청 해시에도 쓴다.
 *
 * @param items 담는 순서. 1개 이상
 */
public record RoomPlaceAddCommand(
	List<Item> items
) {

	public RoomPlaceAddCommand {
		items = List.copyOf(items);
	}

	/**
	 * 담을 투어API 장소 하나.
	 *
	 * @param sourceType              AI(추천 결과에서 고름) 또는 SEARCH(검색 결과에서 고름)
	 * @param recommendationRequestId AI 일 때만 값이 있다
	 * @param rank                    AI 일 때만 값이 있다
	 */
	public record Item(
		PlaceSourceType sourceType,
		String providerPlaceId,
		Long recommendationRequestId,
		Integer rank
	) {

		public static Item ai(String providerPlaceId, Long recommendationRequestId, int rank) {
			return new Item(PlaceSourceType.AI, providerPlaceId, recommendationRequestId, rank);
		}

		public static Item search(String providerPlaceId) {
			return new Item(PlaceSourceType.SEARCH, providerPlaceId, null, null);
		}

		public boolean isAi() {
			return sourceType == PlaceSourceType.AI;
		}
	}
}
