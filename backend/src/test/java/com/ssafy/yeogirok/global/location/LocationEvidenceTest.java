package com.ssafy.yeogirok.global.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.json.JsonMapper;

/** 위치 증거 읽기(C008·C002)와 정확도 기준(C010). 좌표 범위·유효 시각은 발자취 테스트가 본다. */
class LocationEvidenceTest {

	static final JsonMapper MAPPER = JsonMapper.builder().build();
	/** 2026-11-08 12:00:00+09 */
	static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");
	final LocationEvidencePolicy policy = new LocationEvidencePolicy(new LocationEvidenceProperties(
		Duration.ofMinutes(2), Duration.ofSeconds(30), "test-v1", new BigDecimal("30")));

	@Test
	void 정확도까지_읽는다() {
		LocationEvidence evidence = read("""
			{"latitude":37.5,"longitude":127.0,"accuracyMeters":8.5,"capturedAt":"2026-11-08 12:00:00+09"}""");

		assertThat(evidence.accuracyMeters()).isEqualByComparingTo("8.5");
		assertThat(evidence.capturedAt()).isEqualTo(NOW);
	}

	@Test
	void 정확도를_받지_않는_API는_보내도_읽지_않는다() {
		LocationEvidence evidence = LocationEvidence.read(MAPPER.readTree("""
			{"latitude":37.5,"longitude":127.0,"accuracyMeters":"x","capturedAt":"2026-11-08 12:00:00+09"}"""), false);

		assertThat(evidence.accuracyMeters()).isNull();
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource(delimiter = '|', textBlock = """
		null                                                                                 | C008
		[]                                                                                   | C002
		{"latitude":37.5,"longitude":127.0,"capturedAt":"2026-11-08 12:00:00+09"}            | C008
		{"latitude":37.5,"longitude":127.0,"accuracyMeters":null,"capturedAt":"2026-11-08 12:00:00+09"} | C008
		{"latitude":37.5,"longitude":127.0,"accuracyMeters":"8","capturedAt":"2026-11-08 12:00:00+09"}  | C002
		{"latitude":37.5,"longitude":127.0,"accuracyMeters":-0.1,"capturedAt":"2026-11-08 12:00:00+09"} | C002
		{"latitude":37.5,"longitude":127.0,"accuracyMeters":8}                               | C008
		""")
	void 없으면_C008_타입_오류는_C002(String json, String code) {
		assertThatThrownBy(() -> read(json))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getExceptionType().getCode()).isEqualTo(code);
	}

	@Test
	void 정확도_30m는_받고_넘으면_C010() {
		assertThatCode(() -> policy.verify(withAccuracy("30"), NOW)).doesNotThrowAnyException();
		assertThatCode(() -> policy.verify(withAccuracy("0"), NOW)).doesNotThrowAnyException();
		assertThatThrownBy(() -> policy.verify(withAccuracy("30.01"), NOW))
			.extracting("exceptionType").isEqualTo(ExceptionType.LOCATION_ACCURACY_INSUFFICIENT);
	}

	@Test
	void 좌표_범위가_정확도보다_정확도가_유효_시각보다_먼저다() {
		LocationEvidence all = new LocationEvidence(new BigDecimal("91"), BigDecimal.ZERO, new BigDecimal("99"),
			NOW.minus(Duration.ofHours(1)));
		LocationEvidence accuracyAndTime = new LocationEvidence(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("99"),
			NOW.minus(Duration.ofHours(1)));

		assertThatThrownBy(() -> policy.verify(all, NOW))
			.extracting("exceptionType").isEqualTo(ExceptionType.COORDINATE_OUT_OF_RANGE);
		assertThatThrownBy(() -> policy.verify(accuracyAndTime, NOW))
			.extracting("exceptionType").isEqualTo(ExceptionType.LOCATION_ACCURACY_INSUFFICIENT);
	}

	private static LocationEvidence withAccuracy(String meters) {
		return new LocationEvidence(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(meters), NOW);
	}

	private static LocationEvidence read(String json) {
		return LocationEvidence.read(MAPPER.readTree(json), true);
	}
}
