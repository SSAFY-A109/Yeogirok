package com.ssafy.yeogirok.domain.footprint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;

public final class FootprintFixture {

	public static final Instant CAPTURED_AT = Instant.parse("2026-11-04T03:00:00Z");

	private FootprintFixture() {
	}

	public static Footprint.FootprintBuilder footprintOf(Long userId) {
		return Footprint.builder()
			.userId(userId)
			.latitude(new BigDecimal("37.5012345"))
			.longitude(new BigDecimal("127.0398765"))
			.locationName("역삼동")
			.address("서울특별시 강남구 역삼동 123")
			.legalAreaCode("1168010100")
			.body("좋았다")
			.categoryCodes(List.of("CAFE", "FOOD"))
			.visitedAt(CAPTURED_AT)
			.locationCapturedAt(CAPTURED_AT)
			.locationPolicyVersion("test-v1");
	}
}
