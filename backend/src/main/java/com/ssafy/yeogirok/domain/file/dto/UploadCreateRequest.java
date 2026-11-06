package com.ssafy.yeogirok.domain.file.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalLong;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredString;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * API-012 요청. 필드 생략 C001, null·타입 오류 C002, 없는 purpose·1 미만 sizeBytes C003.
 * recordId 는 RECORD_PHOTO·RECEIPT 에서 필수(C001). 형식·크기 한도(M002·M001)는 서비스가 본다.
 */
public class UploadCreateRequest {

	@JsonProperty("purpose")
	private JsonNode purpose;

	@JsonProperty("fileName")
	private JsonNode fileName;

	@JsonProperty("contentType")
	private JsonNode contentType;

	@JsonProperty("sizeBytes")
	private JsonNode sizeBytes;

	@JsonProperty("roomId")
	private JsonNode roomId;

	@JsonProperty("recordId")
	private JsonNode recordId;

	public UploadCreateCommand toCommand() {
		FilePurpose purposeValue = purpose(requiredString(purpose, "purpose"));
		String fileNameValue = requiredString(fileName, "fileName");
		String contentTypeValue = requiredString(contentType, "contentType");
		long size = sizeBytes();
		Long room = optionalLong(roomId, "roomId");
		Long record = optionalLong(recordId, "recordId");
		if (purposeValue.recordRequired() && record == null) {
			throw missing("recordId");
		}
		return new UploadCreateCommand(purposeValue, fileNameValue, contentTypeValue, size, room, record);
	}

	private static FilePurpose purpose(String value) {
		return Arrays.stream(FilePurpose.values())
			.filter(candidate -> candidate.name().equals(value))
			.findFirst()
			.orElseThrow(() -> new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"purpose: TIP_PHOTO, RECORD_PHOTO, RECEIPT 중 하나여야 합니다."));
	}

	/** long 범위를 넘는 정수는 어떤 한도보다도 크므로 M001 이다. */
	private long sizeBytes() {
		if (sizeBytes == null) {
			throw missing("sizeBytes");
		}
		if (!sizeBytes.isIntegralNumber()) {
			throw invalidFormat("sizeBytes", "정수여야 합니다.");
		}
		if (!sizeBytes.canConvertToLong()) {
			throw new BusinessException(sizeBytes.bigIntegerValue().signum() > 0
				? ExceptionType.FILE_TOO_LARGE : ExceptionType.NOT_ALLOWED_VALUE);
		}
		long value = sizeBytes.longValue();
		if (value < 1) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "sizeBytes: 1 이상이어야 합니다.");
		}
		return value;
	}
}
