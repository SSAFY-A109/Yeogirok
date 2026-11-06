package com.ssafy.yeogirok.domain.file.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadStatus;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-012 응답. 앱은 uploadUrl 에 uploadMethod·uploadHeaders 를 그대로 써서 파일을 올린다.
 * S3 key·버킷·자격 증명은 넣지 않는다.
 */
public record UploadCreateResponse(
	String uploadId,
	UploadStatus status,
	String contentType,
	long sizeBytes,
	String uploadUrl,
	String uploadMethod,
	Map<String, String> uploadHeaders,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant uploadExpiresAt
) {

	public static UploadCreateResponse of(UploadSession session, String uploadUrl) {
		return new UploadCreateResponse(session.uploadId(), session.status(), session.contentType(),
			session.sizeBytes(), uploadUrl, "PUT", Map.of("Content-Type", session.contentType()),
			session.uploadExpiresAt());
	}
}
