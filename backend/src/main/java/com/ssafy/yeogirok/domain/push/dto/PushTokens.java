package com.ssafy.yeogirok.domain.push.dto;

import com.ssafy.yeogirok.domain.push.entity.UserPushToken;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** API-095·096·007 토큰 값 검사. */
public final class PushTokens {

	private PushTokens() {
	}

	/** 빈 값·공백만 있는 값·최대 길이 초과는 C003. 값은 FCM 이 준 그대로 쓰고 다듬지 않는다. */
	public static String validate(String token) {
		if (token.isBlank()) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "token: 빈 값이나 공백만 있는 값은 쓸 수 없습니다.");
		}
		if (token.length() > UserPushToken.TOKEN_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"token: 최대 %d자입니다.".formatted(UserPushToken.TOKEN_MAX_LENGTH));
		}
		return token;
	}
}
