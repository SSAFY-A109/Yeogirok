package com.ssafy.yeogirok.domain.file.dto;

import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadStatus;

/**
 * API-086 응답. imageId 는 RECORD_PHOTO, materialId 는 RECEIPT 일 때만 있고 TIP_PHOTO 는 둘 다 null 이다.
 *
 * @param sizeBytes 실제 저장된 바이트 수
 */
public record UploadCompleteResponse(String uploadId, UploadStatus status, Long imageId, String materialId,
	String contentType, long sizeBytes) {

	public static UploadCompleteResponse of(UploadSession session) {
		return new UploadCompleteResponse(session.uploadId(), session.status(), session.imageId(), session.materialId(),
			session.contentType(), session.sizeBytes());
	}
}
