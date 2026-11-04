package com.ssafy.yeogirok.global.exception;

import static org.springframework.http.HttpStatus.*;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 오류 코드. 원천은 팀 Notion "API 명세"다. <b>코드 문자열을 바꾸면 앱 동작이 바뀐다</b> — 명세를 먼저 고친다.
 *
 * <p>접두어는 도메인을 뜻한다. {@code C} 공통, {@code A} 인증·인가, {@code F} 발자취.
 *
 * <p>폐기한 코드는 지우지 말고 {@code @Deprecated} 로 남긴다(명세의 재사용 금지 규칙).
 *
 * <p>상수 이름이 {@code HttpStatus} 상수와 같으면 static import 와 겹치므로 다르게 짓는다.
 */
@Getter
@AllArgsConstructor
public enum ExceptionType {

	/* ── 공통 C ──────────────────────────────────────────────── */

	REQUIRED_VALUE_MISSING(BAD_REQUEST, "C001", "필수 요청값이 누락되었습니다."),
	INVALID_VALUE_FORMAT(BAD_REQUEST, "C002", "요청 필드의 타입 또는 형식이 올바르지 않습니다."),
	NOT_ALLOWED_VALUE(BAD_REQUEST, "C003", "허용하지 않는 요청값입니다."),
	VERSION_MISMATCH(CONFLICT, "C004", "리소스 버전이 일치하지 않습니다. 최신 정보를 다시 조회해 주세요."),
	INVALID_CURSOR(BAD_REQUEST, "C006", "페이지 커서가 유효하지 않습니다."),
	PAGE_SIZE_OUT_OF_RANGE(BAD_REQUEST, "C007", "페이지 크기가 허용 범위를 벗어났습니다."),
	INVALID_DATE_RANGE(BAD_REQUEST, "C012", "시작·종료 날짜 또는 시각의 범위·순서가 올바르지 않습니다."),
	INVALID_BBOX(BAD_REQUEST, "C013", "지도 조회 영역의 형식 또는 범위가 올바르지 않습니다."),
	SERVER_ERROR(INTERNAL_SERVER_ERROR, "C014", "일시적인 오류입니다. 잠시 후 다시 시도해 주세요."),
	RESOURCE_NOT_FOUND(NOT_FOUND, "C015", "요청한 경로를 찾을 수 없습니다."),
	HTTP_METHOD_NOT_ALLOWED(METHOD_NOT_ALLOWED, "C016", "허용되지 않은 HTTP 메서드입니다."),
	MEDIA_TYPE_NOT_SUPPORTED(UNSUPPORTED_MEDIA_TYPE, "C017", "지원하지 않는 Content-Type 입니다."),

	/* ── 인증·인가 A ─────────────────────────────────────────── */

	ACCESS_TOKEN_MISSING(UNAUTHORIZED, "A001", "서비스 액세스 토큰이 없습니다."),
	ACCESS_TOKEN_EXPIRED(UNAUTHORIZED, "A002", "서비스 액세스 토큰이 만료되었습니다."),
	ACCESS_TOKEN_INVALID(UNAUTHORIZED, "A003", "서비스 액세스 토큰이 유효하지 않습니다."),
	ROLE_INSUFFICIENT(FORBIDDEN, "A004", "요청에 필요한 권한이 없습니다."),

	/* ── 발자취 F ───────────────────────────────────────────── */

	FOOTPRINT_NOT_FOUND(NOT_FOUND, "F001", "발자취를 찾을 수 없습니다."),
	FOOTPRINT_NOT_OWNER(FORBIDDEN, "F002", "발자취·방명록 작성자 본인이 아닙니다."),

	;

	private final HttpStatus status;
	private final String code;
	private final String message;
}
