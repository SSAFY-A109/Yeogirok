package com.ssafy.yeogirok.domain.tip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.json.JsonMapper;

class TipPlacementsTest {

	static final JsonMapper MAPPER = JsonMapper.builder().build();
	static final int MAX = 8192;
	final TipPlacements placements = new TipPlacements(MAPPER, new TipProperties(50, 5, 10, MAX));

	@Test
	void 앱이_보낸_객체를_그대로_돌려준다() {
		assertThat(placements.toMap(MAPPER.readTree("""
			{"schemaVersion":1,"anchorType":"CLOUD","authorizationMode":"KEYLESS","pose":{"y":[0.1,0.2]}}
			"""))).containsEntry("schemaVersion", 1).containsEntry("anchorType", "CLOUD").containsKey("pose");
	}

	@Test
	void 정확히_8192바이트는_받고_8193바이트는_T007() {
		assertThat(placements.toMap(MAPPER.readTree(objectOfBytes(MAX)))).hasSize(1);
		expect(objectOfBytes(MAX + 1), ExceptionType.TIP_SPATIAL_INVALID);
	}

	@ParameterizedTest
	@ValueSource(strings = {"[]", "\"text\"", "1", "true"})
	void 객체가_아니면_T007(String json) {
		expect(json, ExceptionType.TIP_SPATIAL_INVALID);
	}

	@Test
	void 없거나_null이면_C001() {
		assertThatThrownBy(() -> placements.toMap(null)).extracting("exceptionType")
			.isEqualTo(ExceptionType.REQUIRED_VALUE_MISSING);
		expect("null", ExceptionType.REQUIRED_VALUE_MISSING);
	}

	private void expect(String json, ExceptionType type) {
		assertThatThrownBy(() -> placements.toMap(MAPPER.readTree(json)))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	/** {"k":"aaa…"} 꼴. 감싸는 부분이 8바이트다. */
	private static String objectOfBytes(int bytes) {
		return "{\"k\":\"" + "a".repeat(bytes - 8) + "\"}";
	}
}
