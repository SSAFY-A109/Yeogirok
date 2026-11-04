package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;

/** 좌표 → 법정동 코드·주소·위치명. 판정하지 못하면(결과 없음·실패·키 없음) 예외 없이 빈 값을 돌려준다. */
public interface ReverseGeocoder {

	ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude);
}
