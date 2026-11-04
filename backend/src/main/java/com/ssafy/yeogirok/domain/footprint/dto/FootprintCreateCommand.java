package com.ssafy.yeogirok.domain.footprint.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API-041 요청을 읽은 값. 형식 검사(C001·C002·C008)는 {@link FootprintCreateRequest} 가 끝냈고,
 * 값 검사(C003·C009·C011)는 서비스가 한다. 멱등키의 요청 해시도 이 값으로 계산한다.
 *
 * @param latitude   소수 7자리로 맞춘 위도(DB 컬럼 numeric(10,7)과 같게)
 * @param longitude  소수 7자리로 맞춘 경도
 * @param capturedAt 기기가 위치를 측정한 시각
 * @param body       정리 전 본문. 없으면 null
 * @param categoryCodes 검사 전 카테고리 코드
 */
public record FootprintCreateCommand(
	BigDecimal latitude,
	BigDecimal longitude,
	Instant capturedAt,
	String body,
	List<String> categoryCodes
) {
}
