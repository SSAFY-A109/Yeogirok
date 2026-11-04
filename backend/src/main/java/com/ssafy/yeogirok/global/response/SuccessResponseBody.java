package com.ssafy.yeogirok.global.response;

import lombok.Getter;

/**
 * 성공 응답. 돌려줄 데이터가 없는 빈 성공도 {@code data} 필드를 {@code null} 로 내보낸다({@code { "success": true, "data": null }}).
 */
@Getter
public final class SuccessResponseBody<T> extends ApiResponseBody<T> {
	private final T data;

	public SuccessResponseBody(T data) {
		super(true);
		this.data = data;
	}
}
