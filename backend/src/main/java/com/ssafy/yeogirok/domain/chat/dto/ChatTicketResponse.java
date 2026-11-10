package com.ssafy.yeogirok.domain.chat.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-077 응답. 앱은 {@code connectPath} 로 WebSocket 을 열고 첫 AUTH 프레임에 {@code ticket} 을 담는다.
 * 티켓을 URL 에 넣지 않는다.
 */
public record ChatTicketResponse(
	String ticket,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant expiresAt,
	String connectPath,
	Long roomId
) {
}
