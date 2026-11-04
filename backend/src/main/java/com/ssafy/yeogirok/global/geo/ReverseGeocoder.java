package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;

/**
 * 좌표 → 법정동(리) 코드·주소·위치명. 운영 구현은 {@link KakaoReverseGeocoder}, 테스트는 고정 값을 돌려주는 구현을 쓴다.
 *
 * <p>판정하지 못하면(결과 없음, 외부 서비스 실패·쿼터 초과, 키 없음) 예외를 던지지 않고 비어 있는 값을 돌려준다.
 * 이 값은 표시용이라 저장을 막지 않는다(노션 API-041).
 */
public interface ReverseGeocoder {

	ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude);
}
