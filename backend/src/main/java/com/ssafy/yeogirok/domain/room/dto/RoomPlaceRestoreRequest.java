package com.ssafy.yeogirok.domain.room.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredString;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/** API-094 요청. version·action 생략 C001, null·타입 오류 C002, action 이 RESTORE 가 아니면 C003. */
public class RoomPlaceRestoreRequest {

	static final String RESTORE = "RESTORE";

	@JsonProperty("version")
	private JsonNode version;

	@JsonProperty("action")
	private JsonNode action;

	/** @return 요청한 여행방 version */
	public int toVersion() {
		int versionValue = requiredInt(version, "version");
		if (!RESTORE.equals(requiredString(action, "action"))) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "action: RESTORE 여야 합니다.");
		}
		return versionValue;
	}
}
