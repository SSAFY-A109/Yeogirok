package com.ssafy.yeogirok.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

/**
 * 성공 응답. 돌려줄 데이터가 없는 빈 성공은 {@code data} 필드를 내보내지 않는다({@code { "success": true }}).
 */
@Getter
public final class SuccessResponseBody<T> extends ApiResponseBody<T> {
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final T data;

	public SuccessResponseBody(T data) {
		super(true);
		this.data = data;
	}
}
