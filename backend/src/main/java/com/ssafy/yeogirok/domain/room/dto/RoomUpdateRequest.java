package com.ssafy.yeogirok.domain.room.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * API-023 요청. 생략한 필드는 지금 값을 유지한다.
 * <ul>
 *   <li>version: 생략 C001, null·정수 아님 C002</li>
 *   <li>title·transportMode: 명시한 null 은 C003(해제할 수 없다), 타입 오류 C002</li>
 *   <li>startDate·endDate: 둘 다 생략하면 유지, 둘 다 null 이면 날짜 미정으로 바꾼다. 하나만 보내면 C012</li>
 *   <li>previewOnly: 생략·null 은 false, boolean 아님 C002</li>
 * </ul>
 */
public class RoomUpdateRequest {

	@JsonProperty("version")
	private JsonNode version;

	@JsonProperty("title")
	private JsonNode title;

	@JsonProperty("startDate")
	private JsonNode startDate;

	@JsonProperty("endDate")
	private JsonNode endDate;

	@JsonProperty("transportMode")
	private JsonNode transportMode;

	@JsonProperty("previewOnly")
	private JsonNode previewOnly;

	public RoomUpdateCommand toCommand() {
		int versionValue = requiredInt(version, "version");

		String titleValue = null;
		if (title != null) {
			if (title.isNull()) {
				throw notNullable("title");
			}
			if (!title.isString()) {
				throw invalidFormat("title", "문자열이어야 합니다.");
			}
			titleValue = title.stringValue();
		}

		boolean datesGiven = startDate != null || endDate != null;
		if (datesGiven && (startDate == null || endDate == null)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "startDate 와 endDate 는 함께 보내야 합니다.");
		}

		TransportMode mode = null;
		if (transportMode != null) {
			if (transportMode.isNull()) {
				throw notNullable("transportMode");
			}
			mode = RoomRequestFields.transportMode(transportMode);
		}

		boolean preview = false;
		if (!isAbsent(previewOnly)) {
			if (!previewOnly.isBoolean()) {
				throw invalidFormat("previewOnly", "boolean 이어야 합니다.");
			}
			preview = previewOnly.booleanValue();
		}

		return new RoomUpdateCommand(versionValue, titleValue, datesGiven,
			RoomRequestFields.optionalDate(startDate, "startDate"), RoomRequestFields.optionalDate(endDate, "endDate"),
			mode, preview);
	}

	private static BusinessException notNullable(String field) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, field + ": null 로 바꿀 수 없습니다.");
	}
}
