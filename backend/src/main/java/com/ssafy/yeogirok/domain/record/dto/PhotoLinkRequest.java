package com.ssafy.yeogirok.domain.record.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * API-068 요청. imageIds 생략·null C001, 배열·정수 아님 C002, 100개 초과 C003. 빈 배열은 바꾸지 않고 지금 값을 받는 요청이다(노션 068).
 * 같은 ID 는 한 번만 남긴다(보낸 순서 유지).
 */
public class PhotoLinkRequest {

	/** imageIds 최대 개수(PR #77 리뷰, 11/10 사용자 결정). */
	static final int MAX_IMAGES = 100;

	@JsonProperty("imageIds")
	private JsonNode imageIds;

	public List<Long> toImageIds() {
		VisitRequestFields.requiredArray(imageIds, "imageIds", true, MAX_IMAGES);
		return VisitRequestFields.optionalIds(imageIds, "imageIds", MAX_IMAGES);
	}
}
