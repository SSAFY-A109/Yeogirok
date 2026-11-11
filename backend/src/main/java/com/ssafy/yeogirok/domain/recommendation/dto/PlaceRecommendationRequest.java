package com.ssafy.yeogirok.domain.recommendation.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.*;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * API-034 요청. 본문 없이 보내도 된다.
 * <ul>
 *   <li>limit: 선택. 정수 아님 C002. 범위(1~상한)는 서비스가 검사한다(C003)</li>
 *   <li>excludedPlaces: 선택. 배열·객체 아님, provider·providerPlaceId 가 문자열 아님 C002,
 *       provider 가 TOUR_API 가 아니거나 providerPlaceId 가 비었으면 C003</li>
 * </ul>
 */
public class PlaceRecommendationRequest {

	@JsonProperty("limit")
	private JsonNode limit;

	@JsonProperty("excludedPlaces")
	private JsonNode excludedPlaces;

	public PlaceRecommendationCommand toCommand() {
		Long limitValue = optionalLong(limit, "limit");
		if (limitValue != null && (limitValue < Integer.MIN_VALUE || limitValue > Integer.MAX_VALUE)) {
			throw notAllowed("limit: 허용 범위를 벗어났습니다.");
		}
		return new PlaceRecommendationCommand(limitValue == null ? null : limitValue.intValue(), excluded());
	}

	private List<PlaceRef> excluded() {
		if (isAbsent(excludedPlaces)) {
			return List.of();
		}
		if (!excludedPlaces.isArray()) {
			throw invalidFormat("excludedPlaces", "배열이어야 합니다.");
		}
		List<PlaceRef> refs = new ArrayList<>();
		for (JsonNode item : excludedPlaces) {
			if (!item.isObject()) {
				throw invalidFormat("excludedPlaces", "객체 배열이어야 합니다.");
			}
			String provider = nonNullString(item.get("provider"), "excludedPlaces.provider");
			String providerPlaceId = nonNullString(item.get("providerPlaceId"), "excludedPlaces.providerPlaceId");
			if (!PlaceProvider.TOUR_API.name().equals(provider)) {
				throw notAllowed("excludedPlaces.provider: TOUR_API 여야 합니다.");
			}
			if (providerPlaceId.isBlank()) {
				throw notAllowed("excludedPlaces.providerPlaceId: 비어 있을 수 없습니다.");
			}
			refs.add(new PlaceRef(provider, providerPlaceId));
		}
		return refs;
	}

	private static BusinessException notAllowed(String message) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
	}
}
