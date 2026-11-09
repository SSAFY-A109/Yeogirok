package com.ssafy.yeogirok.domain.tip.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.request.Uuids;

import tools.jackson.databind.JsonNode;

/**
 * API-051 요청. replacementUploadIds 생략은 기존 사진 그대로, 빈 배열은 사진 없이, 명시한 null C001.
 * location·spatialReference·placement 중 하나라도 보내면 새 재배치로 보고 셋 다 받는다(location 누락 C008, 나머지 C001).
 */
public class TipRetryRequest {

	@JsonProperty("replacementUploadIds")
	private JsonNode replacementUploadIds;

	@JsonProperty("location")
	private JsonNode location;

	@JsonProperty("spatialReference")
	private JsonNode spatialReference;

	@JsonProperty("placement")
	private JsonNode placement;

	public TipRetryCommand toCommand() {
		boolean relocating = location != null || spatialReference != null || placement != null;
		return new TipRetryCommand(Uuids.optionalNonNullList(replacementUploadIds, "replacementUploadIds"),
			relocating ? LocationEvidence.read(location, true) : null, spatialReference, placement);
	}

	/**
	 * 요청을 읽은 값. 멱등키 요청 해시도 이 값으로 계산한다.
	 * @param replacementUploadIds null 이면 기존 사진 그대로
	 * @param location             새 재배치일 때만 값이 있다
	 */
	public record TipRetryCommand(List<String> replacementUploadIds, LocationEvidence location,
		JsonNode spatialReference, JsonNode placement) {

		public boolean relocating() {
			return location != null;
		}
	}
}
