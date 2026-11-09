package com.ssafy.yeogirok.domain.tip.entity;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** AR 팁 종류(DB ENUM {@code ar_tip_category}). 포토·맛집·카페·꿀팁·TMI. */
public enum ArTipCategory {
	PHOTO, RESTAURANT, CAFE, TIP, TMI;

	/** @throws BusinessException 빈 문자열·목록 밖(소문자 포함)이면 C003 */
	public static ArTipCategory fromCode(String code) {
		for (ArTipCategory category : values()) {
			if (category.name().equals(code)) {
				return category;
			}
		}
		throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
			"category: PHOTO·RESTAURANT·CAFE·TIP·TMI 중 하나여야 합니다.");
	}
}
