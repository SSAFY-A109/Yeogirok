package com.ssafy.yeogirok.global.request;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 커서 목록 API 의 limit. 생략하면 20, 1~50 밖이면 C007. 목록 API 는 모두 이것을 쓴다(API-020·042). */
public final class PageSize {

	public static final int DEFAULT = 20;
	public static final int MAX = 50;

	private PageSize() {
	}

	/** @return 쓸 페이지 크기 */
	public static int resolve(Integer limit) {
		int size = limit == null ? DEFAULT : limit;
		if (size < 1 || size > MAX) {
			throw new BusinessException(ExceptionType.PAGE_SIZE_OUT_OF_RANGE, "limit: 1~%d 사이여야 합니다.".formatted(MAX));
		}
		return size;
	}
}
