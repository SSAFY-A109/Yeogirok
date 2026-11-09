package com.ssafy.yeogirok.domain.tip.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.json.JsonMapper;

class SpatialReferenceTest {

	static final JsonMapper MAPPER = JsonMapper.builder().build();
	/** 2026-11-08 12:00:00+09 */
	static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");

	@Test
	void 맞는_값은_그대로_읽는다() {
		SpatialReference reference = read("""
			{"provider":"ARCORE_CLOUD_ANCHOR","referenceId":"%s","expiresAt":"2026-11-08 12:00:01+09"}
			""".formatted("a".repeat(255)));

		assertThat(reference.referenceId()).hasSize(255);
		assertThat(reference.expiresAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource(delimiter = '|', textBlock = """
		null                                                                                      | C001
		[]                                                                                        | C002
		{"provider":1,"referenceId":"a","expiresAt":"2026-11-09 12:00:00+09"}                      | C002
		{"provider":"ARCORE_CLOUD_ANCHOR","referenceId":"a","expiresAt":"2026-11-09T12:00:00Z"}     | C002
		{"referenceId":"a","expiresAt":"2026-11-09 12:00:00+09"}                                   | T007
		{"provider":"EXAMPLE_PROVIDER","referenceId":"a","expiresAt":"2026-11-09 12:00:00+09"}     | T007
		{"provider":"ARCORE_CLOUD_ANCHOR","referenceId":"","expiresAt":"2026-11-09 12:00:00+09"}   | T007
		{"provider":"ARCORE_CLOUD_ANCHOR","expiresAt":"2026-11-09 12:00:00+09"}                    | T007
		{"provider":"ARCORE_CLOUD_ANCHOR","referenceId":"a"}                                       | T007
		{"provider":"ARCORE_CLOUD_ANCHOR","referenceId":"a","expiresAt":"2026-11-08 12:00:00+09"}  | T007
		""")
	void 형식_오류는_C002_공간_참조_오류는_T007(String json, String code) {
		expect(json, code);
	}

	@Test
	void referenceId_256자는_T007() {
		expect("""
			{"provider":"ARCORE_CLOUD_ANCHOR","referenceId":"%s","expiresAt":"2026-11-09 12:00:00+09"}
			""".formatted("a".repeat(256)), "T007");
	}

	private static void expect(String json, String code) {
		assertThatThrownBy(() -> read(json))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getExceptionType().getCode()).isEqualTo(code);
	}

	private static SpatialReference read(String json) {
		return SpatialReference.read(MAPPER.readTree(json), NOW);
	}
}
