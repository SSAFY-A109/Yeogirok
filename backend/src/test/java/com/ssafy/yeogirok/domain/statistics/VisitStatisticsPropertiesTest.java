package com.ssafy.yeogirok.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VisitStatisticsPropertiesTest {

	@Test
	void zoom_이상인_단계_중_가장_큰_최소zoom의_배수를_쓴다() {
		VisitStatisticsProperties properties = of("15:1,13:2,11:8,9:32,0:128");

		assertThat(properties.multiplierFor(18)).contains(1);
		assertThat(properties.multiplierFor(15)).contains(1);
		assertThat(properties.multiplierFor(14.9)).contains(2);
		assertThat(properties.multiplierFor(13)).contains(2);
		assertThat(properties.multiplierFor(12)).contains(8);
		assertThat(properties.multiplierFor(9)).contains(32);
		assertThat(properties.multiplierFor(0)).contains(128);
	}

	@Test
	void 순서와_공백은_상관없고_가장_낮은_단계보다_작은_zoom은_맞는_단계가_없다() {
		assertThat(of(" 0:128 , 15:1 ").multiplierFor(16)).contains(1);
		assertThat(of("15:1,9:32").multiplierFor(8)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"15", "15:1:2", "a:1", "15:b", "-1:1", "15:0", "15:1,15:2"})
	void 형식이_틀리면_시작할_때_실패한다(String raw) {
		assertThatThrownBy(() -> of(raw)).isInstanceOf(IllegalArgumentException.class);
	}

	private static VisitStatisticsProperties of(String zoomMultipliers) {
		return new VisitStatisticsProperties("test-v1", new BigDecimal("0.005"), zoomMultipliers, 1);
	}
}
