package com.ssafy.yeogirok.domain.record.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.nonNullString;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.Uuids;

import tools.jackson.databind.JsonNode;

/** API-062 요청. type 생략·null C001·문자열 아님 C002·RECEIPT·PAYMENT 외 C003, uploadId 생략·null C001·UUID 아님 C002. */
public class MaterialLinkRequest {

	@JsonProperty("type")
	private JsonNode type;
	@JsonProperty("uploadId")
	private JsonNode uploadId;

	public MaterialLinkCommand toCommand() {
		String materialType = nonNullString(type, "type");
		if (Arrays.stream(MaterialLinkCommand.Type.values()).noneMatch(value -> value.name().equals(materialType))) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "type: RECEIPT 또는 PAYMENT 여야 합니다.");
		}
		return new MaterialLinkCommand(MaterialLinkCommand.Type.valueOf(materialType),
			Uuids.require(nonNullString(uploadId, "uploadId"), "uploadId"));
	}
}
