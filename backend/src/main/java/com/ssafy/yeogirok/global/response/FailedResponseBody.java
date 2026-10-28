package com.ssafy.yeogirok.global.response;

import lombok.Getter;

/**
 * 실패 응답. 필드 이름은 명세대로 {@code msg} 가 아니라 {@code message} 다.
 *
 * <p>{@code message} 는 개발자용 디버깅 설명이다. 앱은 표시·분기에 쓰지 않고 {@code code} 로만 분기한다.
 */
@Getter
public final class FailedResponseBody extends ApiResponseBody<Void> {
	private final String code;
	private final String message;

	public FailedResponseBody(String code, String message) {
		super(false);
		this.code = code;
		this.message = message;
	}
}
