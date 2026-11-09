package com.ssafy.yeogirok.global.exception;

import static org.springframework.http.HttpStatus.*;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 오류 코드. 원천은 팀 Notion "API 명세"다. <b>코드 문자열을 바꾸면 앱 동작이 바뀐다</b> — 명세를 먼저 고친다.
 *
 * <p>접두어는 도메인을 뜻한다. {@code C} 공통, {@code A} 인증·인가, {@code F} 발자취, {@code ST} 방문 통계, {@code RO} 여행방,
 * {@code RE} 여행 기록, {@code P} 장소, {@code M} 파일.
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
	// API-005 provider 값 오류. 명세대로 코드는 C003 이고 메시지만 구체적이다.
	UNSUPPORTED_PROVIDER(BAD_REQUEST, "C003", "provider 는 google 또는 kakao 여야 합니다."),
	IDEMPOTENCY_KEY_REUSED(CONFLICT, "C005", "같은 멱등키로 다른 내용의 요청을 보냈습니다."),
	INVALID_CURSOR(BAD_REQUEST, "C006", "페이지 커서가 유효하지 않습니다."),
	PAGE_SIZE_OUT_OF_RANGE(BAD_REQUEST, "C007", "페이지 크기가 허용 범위를 벗어났습니다."),
	LOCATION_EVIDENCE_MISSING(UNPROCESSABLE_CONTENT, "C008", "현재 위치 증거가 없습니다."),
	LOCATION_EVIDENCE_EXPIRED(UNPROCESSABLE_CONTENT, "C009", "현재 위치 증거의 유효 시각이 지났습니다. 위치를 다시 확인해 주세요."),
	COORDINATE_OUT_OF_RANGE(BAD_REQUEST, "C011", "위도·경도가 허용 범위를 벗어났습니다."),
	INVALID_DATE_RANGE(BAD_REQUEST, "C012", "시작·종료 날짜 또는 시각의 범위·순서가 올바르지 않습니다."),
	INVALID_BBOX(BAD_REQUEST, "C013", "지도 조회 영역의 형식 또는 범위가 올바르지 않습니다."),
	SERVER_ERROR(INTERNAL_SERVER_ERROR, "C014", "일시적인 오류입니다. 잠시 후 다시 시도해 주세요."),
	RESOURCE_NOT_FOUND(NOT_FOUND, "C015", "요청한 경로를 찾을 수 없습니다."),
	HTTP_METHOD_NOT_ALLOWED(METHOD_NOT_ALLOWED, "C016", "허용되지 않은 HTTP 메서드입니다."),
	MEDIA_TYPE_NOT_SUPPORTED(UNSUPPORTED_MEDIA_TYPE, "C017", "지원하지 않는 Content-Type 입니다."),
	/** 11/3 제안. 공통 오류표 추가는 팀 합의가 필요하다. */
	IDEMPOTENT_REQUEST_IN_PROGRESS(CONFLICT, "C018", "같은 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요."),

	/* ── 인증·인가 A ─────────────────────────────────────────── */

	ACCESS_TOKEN_MISSING(UNAUTHORIZED, "A001", "서비스 액세스 토큰이 없습니다."),
	ACCESS_TOKEN_EXPIRED(UNAUTHORIZED, "A002", "서비스 액세스 토큰이 만료되었습니다."),
	ACCESS_TOKEN_INVALID(UNAUTHORIZED, "A003", "서비스 액세스 토큰이 유효하지 않습니다."),
	ROLE_INSUFFICIENT(FORBIDDEN, "A004", "요청에 필요한 권한이 없습니다."),
	ID_TOKEN_INVALID(UNAUTHORIZED, "A005", "소셜 ID 토큰 검증에 실패했습니다."),
	ID_TOKEN_NONCE_MISMATCH(UNAUTHORIZED, "A006", "ID 토큰의 nonce 가 요청값과 다릅니다."),
	REFRESH_TOKEN_INVALID(UNAUTHORIZED, "A007", "서비스 리프레시 토큰이 유효하지 않습니다."),
	REFRESH_TOKEN_EXPIRED(UNAUTHORIZED, "A008", "서비스 리프레시 토큰이 만료되었습니다."),
	REFRESH_TOKEN_REVOKED(UNAUTHORIZED, "A009", "서비스 리프레시 토큰이 폐기되었습니다."),
	REFRESH_TOKEN_REUSED(UNAUTHORIZED, "A010", "이미 재발급에 사용된 리프레시 토큰입니다."),
	REAUTHENTICATION_REQUIRED(UNAUTHORIZED, "A011", "최근 재인증이 필요합니다."),
	GOOGLE_AUTH_CODE_INVALID(UNAUTHORIZED, "A029", "구글 인증 코드가 유효하지 않거나 다른 계정의 코드입니다."),

	/* ── 발자취 F ───────────────────────────────────────────── */

	FOOTPRINT_NOT_FOUND(NOT_FOUND, "F001", "발자취를 찾을 수 없습니다."),
	FOOTPRINT_NOT_OWNER(FORBIDDEN, "F002", "발자취·방명록 작성자 본인이 아닙니다."),
	FOOTPRINT_TOO_CLOSE_TODAY(CONFLICT, "F003", "오늘(04:00 기준) 반경 500m 안에 이미 남긴 발자취가 있습니다."),

	/* ── 방문 통계 ST ───────────────────────────────────────── */

	UNSUPPORTED_METRIC(BAD_REQUEST, "ST001", "지원하지 않는 방문 통계 지표입니다."),

	/* ── 여행방 RO ──────────────────────────────────────────── */

	/** 없는 방과 마지막 참여자가 나가 삭제된 방을 구분하지 않는다. */
	ROOM_NOT_FOUND(NOT_FOUND, "RO001", "여행방을 찾을 수 없습니다."),
	/** 현재 참여자가 아니다(참여한 적 없거나 나갔다). 참여자 권한은 모두 같아 권한 부족도 이 코드로 알린다. */
	ROOM_NOT_MEMBER(FORBIDDEN, "RO002", "여행방 참여자가 아닙니다."),
	ROOM_INVITE_TOKEN_INVALID(NOT_FOUND, "RO005", "여행방 초대 토큰이 유효하지 않습니다."),
	ROOM_FULL(CONFLICT, "RO007", "여행방 참여 인원이 가득 찼습니다."),
	/** 없는 후보이거나 다른 방의 후보다. 뺀 후보(removed_at)는 찾을 수 있다. */
	ROOM_PLACE_NOT_FOUND(NOT_FOUND, "RO009", "요청한 후보 장소가 해당 여행방에 없습니다."),

	/* ── 여행 기록 RE (RE021 은 노션에서 사용 중단. RE011·RE012 는 초안 API 때 넣는다) ── */

	/** 없는 기록과 방이 삭제된 기록을 구분하지 않는다. */
	RECORD_NOT_FOUND(NOT_FOUND, "RE001", "여행 기록 없음"),
	RECORD_NOT_MEMBER(FORBIDDEN, "RE002", "여행 기록 참여자가 아님"),
	RECORD_EDIT_FORBIDDEN(FORBIDDEN, "RE003", "여행 기록 편집 권한 없음"),
	VISIT_NOT_FOUND(NOT_FOUND, "RE013", "요청한 실제 방문 일정이 해당 기록에 없음"),
	STORY_NOT_FOUND(NOT_FOUND, "RE014", "요청한 이야기가 해당 방문 일정에 없음"),
	STORY_EDIT_FORBIDDEN(FORBIDDEN, "RE015", "일정별 이야기 편집 권한 없음"),
	RECORD_NOT_COMPLETABLE(UNPROCESSABLE_CONTENT, "RE016",
		"여행기 완성 필수조건 미충족: 실제 방문 0개 또는 장소·날짜·방문 시각 미확인"),
	RECORD_ALREADY_EXISTS(CONFLICT, "RE017", "해당 여행방에 여행 기록이 이미 존재함"),
	VISIT_PLACE_MISSING(BAD_REQUEST, "RE018", "실제 방문 일정의 장소 입력 누락"),
	VISIT_HAS_PHOTOS(CONFLICT, "RE020", "삭제 대상 방문에 연결된 사진이 남아 있음"),
	VISIT_STORIES_NOT_CONFIRMED(CONFLICT, "RE023",
		"삭제 대상 방문에 이야기가 있으나 confirmDeleteStories=true 확인이 없음"),
	TRIP_NOT_ENDED(UNPROCESSABLE_CONTENT, "RE024",
		"여행 종료 조건 미충족: 종료일 미정 또는 Asia/Seoul 기준 종료일이 오늘·미래"),

	/* ── 장소 P ─────────────────────────────────────────────── */

	CUSTOM_PLACE_NAME_INVALID(BAD_REQUEST, "P004", "직접 추가할 장소명은 앞뒤 공백을 빼고 1~255자여야 합니다."),

	/* ── 파일 M (M007·M009·M010 은 쓰는 곳이 없어 넣지 않는다) ──────── */

	FILE_TOO_LARGE(CONTENT_TOO_LARGE, "M001", "업로드 파일 크기가 허용 범위를 넘었습니다."),
	FILE_TYPE_NOT_SUPPORTED(UNSUPPORTED_MEDIA_TYPE, "M002", "지원하지 않는 파일 형식입니다. JPEG·PNG·WEBP 만 올릴 수 있습니다."),
	/** 요청한 크기·형식과 실물이 다르거나, 손상됐거나, 검증 중에 객체가 바뀌었다. 새 업로드 세션으로 다시 올린다. */
	FILE_VERIFICATION_FAILED(UNPROCESSABLE_CONTENT, "M003", "파일 검증에 실패했습니다. 다시 업로드해 주세요."),
	FILE_NOT_FOUND(NOT_FOUND, "M004", "파일 또는 업로드 세션을 찾을 수 없습니다."),
	FILE_ACCESS_DENIED(FORBIDDEN, "M005", "파일을 열람하거나 사용할 권한이 없습니다."),
	/** S3 에 객체가 아직 없다. 같은 uploadId 로 다시 시도할 수 있다. */
	FILE_NOT_UPLOADED(UNPROCESSABLE_CONTENT, "M006", "파일 업로드·검증이 끝나지 않았습니다."),
	UPLOAD_TARGET_ACCESS_DENIED(FORBIDDEN, "M008", "파일 업로드 대상 여행방·기록에 접근할 권한이 없습니다."),
	UPLOAD_SESSION_EXPIRED(GONE, "M011", "업로드 세션 유효기간이 지났습니다. 새로 업로드해 주세요."),
	/** 같은 요청으로 다시 시도할 수 있다. */
	FILE_STORAGE_UNAVAILABLE(SERVICE_UNAVAILABLE, "M012", "파일 저장소를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
	UPLOAD_ALREADY_COMPLETED(CONFLICT, "M013", "이미 완료된 업로드 세션에는 업로드 URL 을 다시 발급하지 않습니다."),

	;

	private final HttpStatus status;
	private final String code;
	private final String message;
}
