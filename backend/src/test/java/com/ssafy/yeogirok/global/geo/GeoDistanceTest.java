package com.ssafy.yeogirok.global.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {

	@Test
	void 같은_좌표는_0m() {
		BigDecimal lat = new BigDecimal("37.5");
		BigDecimal lng = new BigDecimal("127.0");

		assertThat(GeoDistance.meters(lat, lng, lat, lng)).isZero();
	}

	@Test
	void 위도_차이는_1도에_약_111195m() {
		double meters = GeoDistance.meters(new BigDecimal("37.5"), new BigDecimal("127.0"),
			new BigDecimal("37.5044966"), new BigDecimal("127.0"));

		assertThat(meters).isCloseTo(500.0, within(0.1));
	}

	@Test
	void 경도_차이는_위도가_높을수록_짧다() {
		// 위도 37.5도에서 경도 0.01도는 약 882m (111195 * cos(37.5도) * 0.01)
		double meters = GeoDistance.meters(new BigDecimal("37.5"), new BigDecimal("127.00"),
			new BigDecimal("37.5"), new BigDecimal("127.01"));

		assertThat(meters).isCloseTo(882.2, within(0.5));
	}

	@Test
	void 서울역과_강남역은_약_8_9km() {
		// 서울역(37.5547, 126.9707) - 강남역(37.4979, 127.0276). 지도 서비스 직선거리 약 8.0km 대
		double meters = GeoDistance.meters(new BigDecimal("37.5547"), new BigDecimal("126.9707"),
			new BigDecimal("37.4979"), new BigDecimal("127.0276"));

		assertThat(meters).isBetween(7_500.0, 8_500.0);
	}
}
