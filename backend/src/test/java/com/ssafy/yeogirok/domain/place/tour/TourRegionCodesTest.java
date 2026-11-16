package com.ssafy.yeogirok.domain.place.tour;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TourRegionCodesTest {

	@Test
	void 시군구는_뒤_3자리_하나다() {
		assertThat(TourRegionCodes.sido("51150")).isEqualTo("51");
		assertThat(TourRegionCodes.sigungus("51150")).containsExactly("150");
		assertThat(TourRegionCodes.sigungus("12110")).containsExactly("110");
	}

	@ParameterizedTest
	@ValueSource(strings = {"41591", "41593", "41595", "41597"})
	void 화성시_일반구는_옛_화성시_590도_뒤에_붙는다(String regionCode) {
		assertThat(TourRegionCodes.sigungus(regionCode)).containsExactly(regionCode.substring(2), "590");
	}

	@Test
	void 지역이_없거나_시도면_시군구로_거르지_않는다() {
		assertThat(TourRegionCodes.sido(null)).isNull();
		assertThat(TourRegionCodes.sigungus(null)).isEmpty();
		assertThat(TourRegionCodes.sido("41")).isEqualTo("41");
		assertThat(TourRegionCodes.sigungus("41")).isEmpty();
	}
}
