package com.ssafy.yeogirok.global.geo;

/**
 * 법정동. 판정하지 못한 값은 null 이다.
 * @param code 법정동·법정리 코드(10자리)
 * @param name 읍·면·동 이름(region_3depth_name). 법정리·상위 시·군·구는 합치지 않는다
 */
public record LegalDong(String code, String name) {

	public static final LegalDong EMPTY = new LegalDong(null, null);
}
