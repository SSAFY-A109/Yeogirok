package com.ssafy.yeogirok.domain.room.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.*;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * API-029 요청 {@code { items: [{ placeRef, sourceType, recommendationRequestId?, rank? }] }}.
 * <ul>
 *   <li>items: 생략·null C001, 배열 아님 C002, 비었거나 {@link #MAX_ITEMS}개 초과 C003</li>
 *   <li>placeRef: 생략·null C001, 객체 아님 C002. provider 가 TOUR_API 가 아니거나 providerPlaceId 가 비면 C003</li>
 *   <li>sourceType: 생략·null C001, 문자열 아님 C002. AI·SEARCH 가 아니면 C003</li>
 *   <li>recommendationRequestId·rank: AI 면 필수(생략·null C001, 정수 아님 C002), rank 는 1 이상(C003). SEARCH 면 보지 않는다</li>
 * </ul>
 */
public class RoomPlaceAddRequest {

	/** 한 번에 담을 수 있는 수. 추천 한 번의 최대 개수(limit 상한 50)와 같다. */
	public static final int MAX_ITEMS = 50;

	@JsonProperty("items")
	private JsonNode items;

	public RoomPlaceAddCommand toCommand() {
		if (isAbsent(items)) {
			throw missing("items");
		}
		if (!items.isArray()) {
			throw invalidFormat("items", "배열이어야 합니다.");
		}
		if (items.isEmpty() || items.size() > MAX_ITEMS) {
			throw notAllowed("items: 1~" + MAX_ITEMS + "개여야 합니다.");
		}
		List<RoomPlaceAddCommand.Item> parsed = new ArrayList<>();
		for (JsonNode item : items) {
			if (!item.isObject()) {
				throw invalidFormat("items", "객체 배열이어야 합니다.");
			}
			parsed.add(item(item));
		}
		return new RoomPlaceAddCommand(parsed);
	}

	private static RoomPlaceAddCommand.Item item(JsonNode item) {
		JsonNode placeRef = item.get("placeRef");
		if (isAbsent(placeRef)) {
			throw missing("items.placeRef");
		}
		if (!placeRef.isObject()) {
			throw invalidFormat("items.placeRef", "객체여야 합니다.");
		}
		String provider = nonNullString(placeRef.get("provider"), "items.placeRef.provider");
		String providerPlaceId = nonNullString(placeRef.get("providerPlaceId"), "items.placeRef.providerPlaceId");
		if (!PlaceProvider.TOUR_API.name().equals(provider)) {
			throw notAllowed("items.placeRef.provider: TOUR_API 여야 합니다.");
		}
		if (providerPlaceId.isBlank()) {
			throw notAllowed("items.placeRef.providerPlaceId: 비어 있을 수 없습니다.");
		}

		String sourceType = nonNullString(item.get("sourceType"), "items.sourceType");
		if (PlaceSourceType.SEARCH.name().equals(sourceType)) {
			return RoomPlaceAddCommand.Item.search(providerPlaceId);
		}
		if (!PlaceSourceType.AI.name().equals(sourceType)) {
			throw notAllowed("items.sourceType: AI 또는 SEARCH 여야 합니다.");
		}
		long requestId = requiredLong(item.get("recommendationRequestId"), "items.recommendationRequestId");
		JsonNode rankNode = item.get("rank");
		if (isAbsent(rankNode)) {
			throw missing("items.rank");
		}
		int rank = requiredInt(rankNode, "items.rank");
		if (rank < 1) {
			throw notAllowed("items.rank: 1 이상이어야 합니다.");
		}
		return RoomPlaceAddCommand.Item.ai(providerPlaceId, requestId, rank);
	}

	private static BusinessException notAllowed(String message) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
	}
}
