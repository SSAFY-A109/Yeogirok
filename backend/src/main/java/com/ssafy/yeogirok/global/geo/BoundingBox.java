package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 지도 조회 영역 {@code west,south,east,north}. west &lt; east, south &lt; north(날짜 변경선 불가), 어긋나면 C013. */
public record BoundingBox(BigDecimal west, BigDecimal south, BigDecimal east, BigDecimal north) {

	private static final BigDecimal MAX_LONGITUDE = Coordinates.MAX_LONGITUDE;
	private static final BigDecimal MAX_LATITUDE = Coordinates.MAX_LATITUDE;

	/** @return 값이 없으면 null */
	public static BoundingBox parseOrNull(String raw) {
		if (raw == null) {
			return null;
		}
		String[] parts = raw.split(",", -1);
		if (parts.length != 4) {
			throw invalid("west,south,east,north 4개 값이 필요합니다.");
		}
		BoundingBox box = new BoundingBox(number(parts[0]), number(parts[1]), number(parts[2]), number(parts[3]));
		if (outOf(box.west, MAX_LONGITUDE) || outOf(box.east, MAX_LONGITUDE)
			|| outOf(box.south, MAX_LATITUDE) || outOf(box.north, MAX_LATITUDE)) {
			throw invalid("위도·경도 범위를 벗어났습니다.");
		}
		if (box.west.compareTo(box.east) >= 0 || box.south.compareTo(box.north) >= 0) {
			throw invalid("west < east, south < north 여야 합니다.");
		}
		return box;
	}

	private static BigDecimal number(String part) {
		try {
			return new BigDecimal(part.strip());
		} catch (NumberFormatException e) {
			throw invalid("숫자가 아닌 값이 있습니다.");
		}
	}

	private static boolean outOf(BigDecimal value, BigDecimal max) {
		return value.abs().compareTo(max) > 0;
	}

	private static BusinessException invalid(String reason) {
		return new BusinessException(ExceptionType.INVALID_BBOX, "bbox: " + reason);
	}
}
