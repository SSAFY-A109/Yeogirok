package com.ssafy.yeogirok.global.exception;

import lombok.Getter;

/**
 * 규칙 위반. 코드는 {@link ExceptionType} 이 정하고, 메시지는 디버깅에 도움이 되면 덮어쓸 수 있다.
 */
@Getter
public class BusinessException extends RuntimeException {
	private final ExceptionType exceptionType;

	public BusinessException(ExceptionType exceptionType) {
		super(exceptionType.getMessage());
		this.exceptionType = exceptionType;
	}

	public BusinessException(ExceptionType exceptionType, String message) {
		super(message);
		this.exceptionType = exceptionType;
	}
}
