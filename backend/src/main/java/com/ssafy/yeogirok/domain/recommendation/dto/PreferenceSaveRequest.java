package com.ssafy.yeogirok.domain.recommendation.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.*;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * API-033 요청.
 * <ul>
 *   <li>version: 조건 version. 생략 C001, null·정수 아님 C002</li>
 *   <li>regionCode: 생략·null C001, 문자열 아님 C002, 시/군/구 법정동 코드 5자리 숫자가 아니면 C003</li>
 *   <li>categoryCodes: 생략·null C001, 배열 아님·문자열 아닌 항목 C002, 비었거나 없는 코드 C003. 중복은 합친다</li>
 *   <li>skipped: 생략·null 이면 false, 불리언 아님 C002, true 면 C003(건너뛰기 없음)</li>
 * </ul>
 */
public class PreferenceSaveRequest {

	private static final Pattern DISTRICT_CODE = Pattern.compile("\\d{5}");

	@JsonProperty("version")
	private JsonNode version;

	@JsonProperty("regionCode")
	private JsonNode regionCode;

	@JsonProperty("categoryCodes")
	private JsonNode categoryCodes;

	@JsonProperty("skipped")
	private JsonNode skipped;

	public PreferenceSaveCommand toCommand() {
		int versionValue = requiredInt(version, "version");
		String region = nonNullString(regionCode, "regionCode");
		if (!DISTRICT_CODE.matcher(region).matches()) {
			throw notAllowed("regionCode: 시/군/구 법정동 코드 5자리여야 합니다.");
		}
		checkNotSkipped();
		return new PreferenceSaveCommand(versionValue, region, categories().stream().toList());
	}

	private Set<String> categories() {
		if (isAbsent(categoryCodes)) {
			throw missing("categoryCodes");
		}
		if (!categoryCodes.isArray()) {
			throw invalidFormat("categoryCodes", "배열이어야 합니다.");
		}
		Set<String> codes = new LinkedHashSet<>();
		for (JsonNode code : categoryCodes) {
			if (!code.isString()) {
				throw invalidFormat("categoryCodes", "문자열 배열이어야 합니다.");
			}
			if (TravelCategory.fromCode(code.stringValue()).isEmpty()) {
				throw notAllowed("categoryCodes: 없는 카테고리입니다: " + code.stringValue());
			}
			codes.add(code.stringValue());
		}
		if (codes.isEmpty()) {
			throw notAllowed("categoryCodes: 1개 이상 선택해야 합니다.");
		}
		return codes;
	}

	private void checkNotSkipped() {
		if (isAbsent(skipped)) {
			return;
		}
		if (!skipped.isBoolean()) {
			throw invalidFormat("skipped", "불리언이어야 합니다.");
		}
		if (skipped.booleanValue()) {
			throw notAllowed("skipped: 건너뛰기는 제공하지 않습니다.");
		}
	}

	private static BusinessException notAllowed(String message) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
	}
}
