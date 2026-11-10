package com.ssafy.yeogirok.domain.record.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.nonNullString;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * API-091 요청(명세 specs/record-api-2.md 4-5). 누락·null C001, 형식 오류 C002, 공백만인 상호·장소 둘 다·잘못된 provider C003,
 * 좌표 범위 C011, 직접 입력 장소 이름 P004. paidAt 은 방문 시각과 같이 +09 만 받는다. 장소 규칙은 066·067 과 같다.
 */
public class MaterialReviewRequest {

	@JsonProperty("version")
	private JsonNode version;
	@JsonProperty("merchantName")
	private JsonNode merchantName;
	@JsonProperty("paidAt")
	private JsonNode paidAt;
	@JsonProperty("placeRef")
	private JsonNode placeRef;
	@JsonProperty("customPlace")
	private JsonNode customPlace;

	public MaterialReviewCommand toCommand() {
		int expectedVersion = VisitRequestFields.requiredInt(version, "version");
		String merchant = nonNullString(merchantName, "merchantName").strip();
		if (merchant.isEmpty()) {
			throw VisitRequestFields.notAllowed("merchantName: 비어 있을 수 없습니다.");
		}
		var paid = VisitRequestFields.dateTime(paidAt, "paidAt");
		VisitPlaceInput place = VisitRequestFields.place(placeRef, customPlace);
		if (place == null) {
			throw missing("placeRef 또는 customPlace");
		}
		return new MaterialReviewCommand(expectedVersion, merchant, paid, place);
	}
}
