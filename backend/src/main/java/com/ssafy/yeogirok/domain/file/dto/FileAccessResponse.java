package com.ssafy.yeogirok.domain.file.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-013 응답. url 은 확정 객체의 Presigned GET URL 이고, 앱은 저장하지 않고 만료되면 이 API 를 다시 부른다.
 *
 * @param expiresAt URL 실제 만료 시각
 */
public record FileAccessResponse(
	String url,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant expiresAt,
	String contentType
) {
}
