package com.ssafy.yeogirok.global.exception;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 컨트롤러 단계의 예외 → 공통 실패 봉투.
 *
 * <p>시큐리티 필터 단계(401·403)는 여기까지 오지 않는다. {@code global.security} 의 EntryPoint·AccessDeniedHandler 가 맡는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** 이 제약 위반은 "값이 없다"(C001), 나머지 제약 위반은 "허용하지 않는 값"(C003)이다. */
	private static final Set<String> MISSING_CONSTRAINTS = Set.of("NotNull", "NotBlank", "NotEmpty");

	private static ResponseEntity<ApiResponseBody<Void>> respond(ExceptionType type) {
		return ResponseEntity.status(type.getStatus()).body(ApiResponseUtil.createFailureResponse(type));
	}

	private static ResponseEntity<ApiResponseBody<Void>> respond(ExceptionType type, String message) {
		return ResponseEntity.status(type.getStatus()).body(ApiResponseUtil.createFailureResponse(type, message));
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponseBody<Void>> businessException(BusinessException e) {
		return respond(e.getExceptionType(), e.getMessage());
	}

	/** {@code @Valid} 요청 본문 위반. 첫 오류 하나로 코드를 정한다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponseBody<Void>> methodArgumentNotValid(MethodArgumentNotValidException e) {
		ObjectError error = e.getBindingResult().getAllErrors().getFirst();
		String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
		String message = "%s: %s".formatted(field, error.getDefaultMessage());

		return MISSING_CONSTRAINTS.contains(error.getCode())
			? respond(ExceptionType.REQUIRED_VALUE_MISSING, message)
			: respond(ExceptionType.NOT_ALLOWED_VALUE, message);
	}

	/** 컨트롤러 파라미터에 직접 붙인 제약 위반. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponseBody<Void>> handlerMethodValidation(HandlerMethodValidationException e) {
		return respond(ExceptionType.NOT_ALLOWED_VALUE, e.getAllErrors().getFirst().getDefaultMessage());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponseBody<Void>> missingParameter(MissingServletRequestParameterException e) {
		return respond(ExceptionType.REQUIRED_VALUE_MISSING, "%s 파라미터가 필요합니다.".formatted(e.getParameterName()));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ApiResponseBody<Void>> missingHeader(MissingRequestHeaderException e) {
		return respond(ExceptionType.REQUIRED_VALUE_MISSING, "%s 헤더가 필요합니다.".formatted(e.getHeaderName()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponseBody<Void>> typeMismatch(MethodArgumentTypeMismatchException e) {
		return respond(ExceptionType.INVALID_VALUE_FORMAT, "%s 형식이 올바르지 않습니다.".formatted(e.getName()));
	}

	/** 본문이 JSON 이 아니거나 필드 타입이 맞지 않는 경우. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponseBody<Void>> unreadableBody(HttpMessageNotReadableException e) {
		return respond(ExceptionType.INVALID_VALUE_FORMAT, "요청 본문 형식이 올바르지 않습니다.");
	}

	/** {@code @PreAuthorize} 등 메서드 보안 거부. URL 단위 거부는 RestAccessDeniedHandler 가 맡는다. */
	@ExceptionHandler(AuthorizationDeniedException.class)
	public ResponseEntity<ApiResponseBody<Void>> authorizationDenied(AuthorizationDeniedException e) {
		return respond(ExceptionType.ROLE_INSUFFICIENT);
	}

	/* ── HTTP 프로토콜 수준. 정상 클라이언트는 만날 일이 없다 ─────────── */

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponseBody<Void>> noResource(NoResourceFoundException e) {
		return respond(ExceptionType.RESOURCE_NOT_FOUND);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponseBody<Void>> methodNotSupported(HttpRequestMethodNotSupportedException e) {
		return respond(ExceptionType.HTTP_METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponseBody<Void>> mediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
		return respond(ExceptionType.MEDIA_TYPE_NOT_SUPPORTED);
	}

	/**
	 * 마지막 그물. 원인은 스택까지 로그에만 남기고, 응답에는 고정 문구만 보낸다
	 * (예외 메시지에 SQL 조각·테이블 이름이 섞여 나갈 수 있다).
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponseBody<Void>> unexpected(Exception e) {
		log.error("Unexpected server error", e);
		return respond(ExceptionType.SERVER_ERROR);
	}
}
