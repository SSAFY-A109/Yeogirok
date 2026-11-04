package com.ssafy.yeogirok.domain.footprint.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API-041 요청을 읽은 값. 값 검사는 서비스가 하고, 멱등키 요청 해시도 이 값으로 계산한다.
 *
 * @param latitude   소수 7자리로 맞춘 위도(DB numeric(10,7))
 * @param longitude  소수 7자리로 맞춘 경도
 * @param body       정리 전 본문. 없으면 null
 */
public record FootprintCreateCommand(
	BigDecimal latitude,
	BigDecimal longitude,
	Instant capturedAt,
	String body,
	List<String> categoryCodes
) {
}
