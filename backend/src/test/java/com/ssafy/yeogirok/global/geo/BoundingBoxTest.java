package com.ssafy.yeogirok.global.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

class BoundingBoxTest {

	@Test
	void west_south_east_north_순서로_읽는다() {
		BoundingBox box = BoundingBox.parseOrNull("126.9, 37.4, 127.1,37.6");

		assertThat(box.west()).isEqualByComparingTo(new BigDecimal("126.9"));
		assertThat(box.south()).isEqualByComparingTo(new BigDecimal("37.4"));
		assertThat(box.east()).isEqualByComparingTo(new BigDecimal("127.1"));
		assertThat(box.north()).isEqualByComparingTo(new BigDecimal("37.6"));
	}

	@Test
	void 범위_끝값도_받는다() {
		assertThat(BoundingBox.parseOrNull("-180,-90,180,90")).isNotNull();
	}

	@Test
	void 값이_없으면_null() {
		assertThat(BoundingBox.parseOrNull(null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"",                          // 빈 값
		"126.9,37.4,127.1",          // 3개
		"126.9,37.4,127.1,37.6,1",   // 5개
		"126.9,37.4,127.1,",         // 빈 칸
		"a,37.4,127.1,37.6",         // 숫자 아님
		"-180.1,37.4,127.1,37.6",    // 경도 범위 밖
		"126.9,37.4,180.1,37.6",
		"126.9,-90.1,127.1,37.6",    // 위도 범위 밖
		"126.9,37.4,127.1,90.1",
		"127.1,37.4,126.9,37.6",     // west > east
		"126.9,37.6,127.1,37.4",     // south > north
		"126.9,37.4,126.9,37.6",     // west = east
		"170,37.4,-170,37.6"         // 날짜 변경선을 넘는 영역
	})
	void 형식이나_범위가_틀리면_C013(String raw) {
		assertThatThrownBy(() -> BoundingBox.parseOrNull(raw))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.INVALID_BBOX);
	}
}
