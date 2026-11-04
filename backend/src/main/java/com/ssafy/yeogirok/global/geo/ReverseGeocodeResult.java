package com.ssafy.yeogirok.global.geo;

/**
 * 역지오코딩 결과. 판정하지 못한 값은 null 이다.
 *
 * @param legalAreaCode 법정동·법정리 코드(10자리)
 * @param address       주소. 도로명 주소가 있으면 도로명, 없으면 지번 주소
 * @param locationName  위치명. 건물 이름이 있으면 건물 이름, 없으면 법정동·법정리 이름
 */
public record ReverseGeocodeResult(String legalAreaCode, String address, String locationName) {

	public static final ReverseGeocodeResult EMPTY = new ReverseGeocodeResult(null, null, null);
}
