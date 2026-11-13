package com.ssafy.yeogirok.global.request;

import java.util.regex.Pattern;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 조회 조건 regionCode. 법정동 시·도 2자리 또는 시·군·구 5자리 숫자다. 코스 검색(API-014·015)·장소 검색(API-026)이 같이 쓴다.
 * 형식이 틀렸을 때의 오류 코드는 API 마다 달라(014 CO002, 015 C003, 026 P002) 부르는 쪽이 넘긴다.
 * 추천 지역 저장(API-033)은 시·군·구 5자리만 받는 다른 규칙이라 쓰지 않는다.
 */
public final class RegionCode {

	private static final Pattern PATTERN = Pattern.compile("\\d{2}|\\d{5}");

	private RegionCode() {
	}

	/** 없으면(null) 통과한다. 있는데 형식이 틀리면 {@code onInvalid}. */
	public static void check(String regionCode, ExceptionType onInvalid) {
		if (regionCode != null && !PATTERN.matcher(regionCode).matches()) {
			throw new BusinessException(onInvalid, "regionCode: 시·도 2자리 또는 시·군·구 5자리 숫자여야 합니다.");
		}
	}
}
