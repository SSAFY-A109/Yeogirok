package com.ssafy.yeogirok.domain.tip.entity;

import static com.ssafy.yeogirok.global.request.JsonFields.dateTime;
import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import tools.jackson.databind.JsonNode;

/**
 * AR 공간 참조(ARCore Cloud Anchor). 서버는 공급자에 앵커가 있는지 조회하지 않는다(specs/ar-tip-api.md 1절).
 * 요청·응답에 같은 모양으로 쓴다.
 */
public record SpatialReference(
	String provider,
	String referenceId,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant expiresAt
) {

	public static final String PROVIDER = "ARCORE_CLOUD_ANCHOR";
	public static final int MAX_REFERENCE_ID_LENGTH = 255;

	/**
	 * 객체가 없거나 null C001, 객체 아님·필드 타입·expiresAt 형식 오류 C002,
	 * provider 가 다름·referenceId 1~255자 밖·expiresAt 없음 또는 {@code now} 이전이면 T007.
	 */
	public static SpatialReference read(JsonNode node, Instant now) {
		if (isAbsent(node)) {
			throw missing("spatialReference");
		}
		if (!node.isObject()) {
			throw invalidFormat("spatialReference", "객체여야 합니다.");
		}
		String provider = string(node, "provider");
		String referenceId = string(node, "referenceId");
		JsonNode rawExpiresAt = node.get("expiresAt");
		Instant expiresAt = isAbsent(rawExpiresAt) ? null : dateTime(rawExpiresAt, "spatialReference.expiresAt");

		if (!PROVIDER.equals(provider)) {
			throw invalid("provider 는 " + PROVIDER + " 여야 합니다.");
		}
		if (referenceId == null || referenceId.isEmpty() || referenceId.length() > MAX_REFERENCE_ID_LENGTH) {
			throw invalid("referenceId 는 1~" + MAX_REFERENCE_ID_LENGTH + "자여야 합니다.");
		}
		if (expiresAt == null || !expiresAt.isAfter(now)) {
			throw invalid("expiresAt 은 지금보다 뒤 시각이어야 합니다.");
		}
		return new SpatialReference(provider, referenceId, expiresAt);
	}

	/** @return 없거나 null 이면 null(T007 로 알린다) */
	private static String string(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (isAbsent(value)) {
			return null;
		}
		if (!value.isString()) {
			throw invalidFormat("spatialReference." + field, "문자열이어야 합니다.");
		}
		return value.stringValue();
	}

	private static BusinessException invalid(String reason) {
		return new BusinessException(ExceptionType.TIP_SPATIAL_INVALID, "spatialReference: " + reason);
	}
}
