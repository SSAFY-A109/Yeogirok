package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;

/** 두 좌표 사이의 지표면 거리(하버사인 공식, 지구를 반지름 6,371,008.8m 구로 본다). 수백 m 범위에서 오차는 1% 미만이다. */
public final class GeoDistance {

	static final double EARTH_RADIUS_METERS = 6_371_008.8;

	private GeoDistance() {
	}

	public static double meters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
		double phi1 = Math.toRadians(lat1.doubleValue());
		double phi2 = Math.toRadians(lat2.doubleValue());
		double dPhi = phi2 - phi1;
		double dLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
		double h = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
			+ Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
	}
}
