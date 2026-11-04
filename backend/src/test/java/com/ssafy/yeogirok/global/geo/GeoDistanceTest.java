package com.ssafy.yeogirok.global.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {

	@Test
	void 같은_좌표는_0m_위도_차이는_1도에_약_111195m() {
		BigDecimal lat = new BigDecimal("37.5");
		BigDecimal lng = new BigDecimal("127.0");
		assertThat(GeoDistance.meters(lat, lng, lat, lng)).isZero();

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
}
