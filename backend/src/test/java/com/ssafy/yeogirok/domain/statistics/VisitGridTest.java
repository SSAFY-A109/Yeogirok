package com.ssafy.yeogirok.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.global.geo.BoundingBox;

class VisitGridTest {

	final VisitGrid grid = new VisitGrid(new BigDecimal("0.005"), 2);  // 한 변 0.01도

	@Test
	void 셀_한_변은_기본_셀_곱하기_배수() {
		assertThat(grid.cellSize()).isEqualByComparingTo("0.01");
		assertThat(grid.precision()).isEqualTo("0.01deg");
	}

	@Test
	void bbox를_걸치는_셀_경계까지_넓힌다() {
		BoundingBox expanded = grid.expandToCells(BoundingBox.parseOrNull("127.003,37.501,127.018,37.519"));

		assertThat(expanded.west()).isEqualByComparingTo("127.00");
		assertThat(expanded.south()).isEqualByComparingTo("37.50");
		assertThat(expanded.east()).isEqualByComparingTo("127.02");
		assertThat(expanded.north()).isEqualByComparingTo("37.52");
	}

	@Test
	void 경계_위의_bbox는_그_경계를_포함하도록_다음_칸까지_넓힌다() {
		BoundingBox expanded = grid.expandToCells(BoundingBox.parseOrNull("127.00,37.50,127.02,37.52"));

		assertThat(expanded.east()).isEqualByComparingTo("127.03");
		assertThat(expanded.north()).isEqualByComparingTo("37.53");
	}

	@Test
	void 기본_셀_칸_번호를_큰_셀_칸_번호로_묶는다_음수도_내림() {
		assertThat(grid.toCellIndex(7500)).isEqualTo(3750);
		assertThat(grid.toCellIndex(7501)).isEqualTo(3750);
		assertThat(grid.toCellIndex(-1)).isEqualTo(-1);
		assertThat(grid.toCellIndex(-2)).isEqualTo(-1);
		assertThat(grid.toCellIndex(-3)).isEqualTo(-2);
	}

	@Test
	void 셀_중심과_셀_ID() {
		assertThat(grid.center(3750)).isEqualByComparingTo("37.505");
		assertThat(grid.center(-1)).isEqualByComparingTo("-0.005");
		assertThat(grid.cellId(3750, 12700)).isEqualTo("2:3750:12700");
	}
}
