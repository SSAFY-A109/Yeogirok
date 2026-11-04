package com.ssafy.yeogirok.global.geo;

/**
 * 역지오코딩 결과. 판정하지 못한 값은 null 이다.
 * @param address      도로명 주소, 없으면 지번 주소
 * @param locationName 건물 이름, 없으면 법정동·법정리 이름
 */
public record ReverseGeocodeResult(String legalAreaCode, String address, String locationName) {

	public static final ReverseGeocodeResult EMPTY = new ReverseGeocodeResult(null, null, null);
}
