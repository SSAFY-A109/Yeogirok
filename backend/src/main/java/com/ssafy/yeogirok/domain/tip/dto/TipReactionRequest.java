package com.ssafy.yeogirok.domain.tip.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.nonNullString;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.tip.entity.TipReaction;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;

import tools.jackson.databind.JsonNode;

/** API-052 요청. reaction 없음·null C001, 문자열 아님 C002, LIKE·DISLIKE·NONE 밖 C003. location 은 C008·C002. */
public class TipReactionRequest {

	private static final String NONE = "NONE";

	@JsonProperty("reaction")
	private JsonNode reaction;

	@JsonProperty("location")
	private JsonNode location;

	public TipReactionCommand toCommand() {
		String value = nonNullString(reaction, "reaction");
		TipReaction parsed = null;
		if (!NONE.equals(value)) {
			try {
				parsed = TipReaction.valueOf(value);
			} catch (IllegalArgumentException e) {
				throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "reaction: LIKE·DISLIKE·NONE 중 하나여야 합니다.");
			}
		}
		return new TipReactionCommand(parsed, LocationEvidence.read(location, true));
	}

	/** @param reaction 바꿀 최종 상태. NONE(취소)이면 null */
	public record TipReactionCommand(TipReaction reaction, LocationEvidence location) {
	}
}
