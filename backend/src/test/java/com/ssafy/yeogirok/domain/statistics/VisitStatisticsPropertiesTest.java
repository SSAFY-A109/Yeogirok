package com.ssafy.yeogirok.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

	/** 로컬 격자(S15P31A109-213): 기본 셀 0.0001도. zoom 16 이상은 촘촘하게, 15 이하는 이전 격자와 같은 칸 크기. */
	static final String LOCAL_MULTIPLIERS = "19:1,18:2,17:5,16:10,15:50,13:100,11:400,9:1600,0:6400";

	@Test
	void 로컬_배수_문자열은_9단계로_읽힌다() {
		assertThat(ZoomMultipliers.valueOf(LOCAL_MULTIPLIERS).levels()).hasSize(9);
	}

	@ParameterizedTest
	@CsvSource({"19,0.0001", "18,0.0002", "17,0.0005", "16,0.001",
		"15,0.005", "13,0.01", "11,0.04", "9,0.16", "0,0.64"})
	void 로컬_격자의_zoom별_칸_크기(int zoom, String cellSize) {
		VisitGrid grid = new VisitGrid(new BigDecimal("0.0001"),
			ZoomMultipliers.valueOf(LOCAL_MULTIPLIERS).multiplierFor(zoom));

		assertThat(grid.cellSize()).isEqualByComparingTo(cellSize);
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
