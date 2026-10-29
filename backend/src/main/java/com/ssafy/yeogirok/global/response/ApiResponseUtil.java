package com.ssafy.yeogirok.global.response;

import com.ssafy.yeogirok.global.exception.ExceptionType;

public final class ApiResponseUtil {

	private ApiResponseUtil() {
	}

	public static ApiResponseBody<Void> createSuccessResponse() {
		return new SuccessResponseBody<>(null);
	}

	public static <T> ApiResponseBody<T> createSuccessResponse(T data) {
		return new SuccessResponseBody<>(data);
	}

	public static ApiResponseBody<Void> createFailureResponse(ExceptionType exceptionType) {
		return new FailedResponseBody(exceptionType.getCode(), exceptionType.getMessage());
	}

	public static ApiResponseBody<Void> createFailureResponse(ExceptionType exceptionType, String message) {
		return new FailedResponseBody(exceptionType.getCode(), message);
	}
}
