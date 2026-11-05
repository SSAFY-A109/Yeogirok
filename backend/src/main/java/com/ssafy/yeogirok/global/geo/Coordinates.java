package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * 위도·경도 공통 규칙. 저장 컬럼은 NUMERIC(10,7) 이라 소수 7자리로 맞춘다. 범위 밖은 C011.
 * 생략·타입 오류의 코드는 API 마다 달라(C001·C008, C002) 부르는 쪽이 정한다.
 */
public final class Coordinates {

	/** latitude·longitude 컬럼의 소수 자릿수. */
	public static final int SCALE = 7;
	public static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
	public static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

	private Coordinates() {
	}

	/**
	 * 숫자 JSON 값을 소수 7자리로 바꾼다. 숫자인지는 부르는 쪽이 먼저 확인한다.
	 * 1e400 처럼 double 범위를 넘는 값은 Infinity 로 읽혀 decimalValue() 가 실패하므로 범위 밖 좌표와 같은 C011 이다.
	 */
	public static BigDecimal toDecimal(JsonNode number) {
		if (number.isFloatingPointNumber() && !Double.isFinite(number.doubleValue())) {
			throw new BusinessException(ExceptionType.COORDINATE_OUT_OF_RANGE);
		}
		return number.decimalValue().setScale(SCALE, RoundingMode.HALF_UP);
	}

	/** @throws BusinessException 위도 -90~90, 경도 -180~180 밖이면 C011 */
	public static void checkRange(BigDecimal latitude, BigDecimal longitude) {
		if (latitude.abs().compareTo(MAX_LATITUDE) > 0 || longitude.abs().compareTo(MAX_LONGITUDE) > 0) {
			throw new BusinessException(ExceptionType.COORDINATE_OUT_OF_RANGE);
		}
	}
}
