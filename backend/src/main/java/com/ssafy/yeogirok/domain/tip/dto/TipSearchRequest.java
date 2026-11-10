package com.ssafy.yeogirok.domain.tip.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.optionalLong;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalString;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.global.location.LocationEvidence;

import tools.jackson.databind.JsonNode;

/**
 * API-047 요청. location 은 필드 4개 모두 필수(C008), 타입 오류 C002. category 목록 밖 C003.
 * radiusMeters(50만)·limit(1~10) 값 검사는 서비스가 한다. cursor 는 받지만 쓰지 않는다(다음 페이지 없음).
 */
public class TipSearchRequest {

	@JsonProperty("location")
	private JsonNode location;

	@JsonProperty("radiusMeters")
	private JsonNode radiusMeters;

	@JsonProperty("category")
	private JsonNode category;

	@JsonProperty("cursor")
	private JsonNode cursor;

	@JsonProperty("limit")
	private JsonNode limit;

	public TipSearchQuery toQuery() {
		LocationEvidence evidence = LocationEvidence.read(location, true);
		Long radius = optionalLong(radiusMeters, "radiusMeters");
		String categoryCode = optionalString(category, "category");
		optionalString(cursor, "cursor");
		return new TipSearchQuery(evidence, radius, categoryCode == null ? null : ArTipCategory.fromCode(categoryCode),
			optionalLong(limit, "limit"));
	}

	/** @param radiusMeters·limit 생략이면 null, category 생략이면 전체 */
	public record TipSearchQuery(LocationEvidence location, Long radiusMeters, ArTipCategory category, Long limit) {
	}
}
