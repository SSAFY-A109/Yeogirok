package com.ssafy.yeogirok.domain.tip;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.SpatialReference;
import com.ssafy.yeogirok.global.location.LocationEvidence;

public final class TipFixture {

	/** 2026-11-08 12:00:00+09 */
	public static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");
	public static final BigDecimal LAT = new BigDecimal("37.5012345");
	public static final BigDecimal LNG = new BigDecimal("127.0398765");
	/** 위도 1e-5도 ≈ 1.11m. */
	public static final BigDecimal LAT_STEP = new BigDecimal("0.00001");

	private TipFixture() {
	}

	public static LocationEvidence locationAt(BigDecimal latitude, BigDecimal longitude) {
		return new LocationEvidence(latitude, longitude, new BigDecimal("8"), NOW);
	}

	/** 기준 위치에서 북쪽으로 {@code steps} × 약 1.11m. */
	public static LocationEvidence northOf(int steps) {
		return locationAt(LAT.add(LAT_STEP.multiply(BigDecimal.valueOf(steps))), LNG);
	}

	public static ArTip registering(Long authorId) {
		return registering(authorId, ArTipCategory.CAFE, locationAt(LAT, LNG));
	}

	public static ArTip registering(Long authorId, ArTipCategory category, LocationEvidence location) {
		return ArTip.register(authorId, category, "창가 자리가 좋아요", location, "test-v1",
			new SpatialReference(SpatialReference.PROVIDER, "anchor-1", NOW.plusSeconds(86_400)),
			Map.of("schemaVersion", 1, "anchorType", "CLOUD", "authorizationMode", "KEYLESS"), NOW);
	}

	public static ArTip published(Long authorId) {
		return publish(registering(authorId));
	}

	public static ArTip publish(ArTip tip) {
		tip.publish(NOW);
		return tip;
	}
}
