package com.ssafy.yeogirok.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.ssafy.yeogirok.domain.statistics.VisitStatisticsProperties.ZoomMultipliers;

class VisitStatisticsPropertiesTest {

	@Test
	void zoom_이상인_단계_중_가장_큰_최소zoom의_배수를_쓰고_순서와_공백은_상관없다() {
		ZoomMultipliers multipliers = ZoomMultipliers.valueOf(" 0:128 , 15:1,13:2,11:8,9:32");

		assertThat(multipliers.multiplierFor(18)).isEqualTo(1);
		assertThat(multipliers.multiplierFor(15)).isEqualTo(1);
		assertThat(multipliers.multiplierFor(14.9)).isEqualTo(2);
		assertThat(multipliers.multiplierFor(12)).isEqualTo(8);
		assertThat(multipliers.multiplierFor(9)).isEqualTo(32);
		assertThat(multipliers.multiplierFor(0)).isEqualTo(128);
	}

	@ParameterizedTest
	@ValueSource(strings = {"15", "15:1:2", "a:1", "15:b", "-1:1", "15:0", "15:1,15:2,0:4", "15:1,9:32"})
	void 형식이_틀리거나_최소zoom_0_단계가_없으면_실패한다(String raw) {
		assertThatThrownBy(() -> ZoomMultipliers.valueOf(raw)).isInstanceOf(RuntimeException.class);
	}

	@Test
	void 설정_문자열을_시작할_때_파싱해_바인딩하고_틀리면_바인딩이_실패한다() {
		VisitStatisticsProperties properties = bind("10:1,0:4");

		assertThat(properties.zoomMultipliers().multiplierFor(3)).isEqualTo(4);
		assertThat(properties.maxCells()).isEqualTo(2500);
		assertThatThrownBy(() -> bind("10:1")).isInstanceOf(BindException.class);
	}

	private static VisitStatisticsProperties bind(String zoomMultipliers) {
		return new Binder(new MapConfigurationPropertySource(Map.of(
			"yeogirok.visit-statistics.aggregation-version", "test-v1",
			"yeogirok.visit-statistics.base-cell-size-degrees", "0.005",
			"yeogirok.visit-statistics.zoom-multipliers", zoomMultipliers,
			"yeogirok.visit-statistics.min-count", "1",
			"yeogirok.visit-statistics.max-cells", "2500")))
			.bindOrCreate("yeogirok.visit-statistics", VisitStatisticsProperties.class);
	}
}
