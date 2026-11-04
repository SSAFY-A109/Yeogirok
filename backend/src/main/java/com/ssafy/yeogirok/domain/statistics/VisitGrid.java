package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ssafy.yeogirok.global.geo.BoundingBox;

/** 한 zoom 의 위경도 격자(셀 한 변 = 기본 셀 × 배수). 칸 번호가 floor(좌표 / 한 변)라 기본 셀을 그대로 묶을 수 있다. */
record VisitGrid(BigDecimal baseCellSize, int multiplier) {

	private static final int COORDINATE_SCALE = 7;
	private static final BigDecimal HALF = new BigDecimal("0.5");

	BigDecimal cellSize() {
		return baseCellSize.multiply(BigDecimal.valueOf(multiplier));
	}

	/** bbox 에 걸치는 셀 전체를 덮도록 셀 경계로 넓힌 영역. 가장자리 셀이 잘려서 적게 세지지 않게 한다. */
	BoundingBox expandToCells(BoundingBox box) {
		BigDecimal size = cellSize();
		return new BoundingBox(
			lowerEdge(box.west(), size), lowerEdge(box.south(), size),
			upperEdge(box.east(), size), upperEdge(box.north(), size));
	}

	/** footprint_statistics 의 기본 셀 칸 번호를 이 격자의 칸 번호로. */
	long toCellIndex(long baseIndex) {
		return Math.floorDiv(baseIndex, multiplier);
	}

	BigDecimal center(long cellIndex) {
		return BigDecimal.valueOf(cellIndex).add(HALF).multiply(cellSize())
			.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
	}

	String cellId(long latitudeIndex, long longitudeIndex) {
		return multiplier + ":" + latitudeIndex + ":" + longitudeIndex;
	}

	String precision() {
		return cellSize().stripTrailingZeros().toPlainString() + "deg";
	}

	private static BigDecimal lowerEdge(BigDecimal value, BigDecimal size) {
		return value.divide(size, 0, RoundingMode.FLOOR).multiply(size);
	}

	/** 경계 위의 점도 포함하도록 다음 칸의 시작까지 넓힌다(조회는 이 값 미만). */
	private static BigDecimal upperEdge(BigDecimal value, BigDecimal size) {
		return value.divide(size, 0, RoundingMode.FLOOR).add(BigDecimal.ONE).multiply(size);
	}
}
