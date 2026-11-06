package com.ssafy.yeogirok.domain.file.session;

import java.time.Instant;

import com.ssafy.yeogirok.domain.file.FilePurpose;

/**
 * 업로드 세션(Redis {@code upload:{uploadId}}). 명세 {@code specs/file-api.md} 2절.
 * 만료는 상태값이 아니라 {@code uploadExpiresAt} 으로 판정한다. 만료된 미완료 세션은 M011 이어야 해서 키를 바로 지우지 않는다.
 *
 * @param uploadAcceptedAt 012 최초 발급 시각. 영수증 원본 24시간 보관의 기산점이라 재전송·완료로 바꾸지 않는다
 * @param confirmedKey     검증을 마치고 옮긴 확정 경로. READY 전에는 null
 * @param imageId          RECORD_PHOTO 확정 사진(visit_images) ID. 그 외 null
 * @param materialId       RECEIPT 자료 ID. 그 외 null
 */
public record UploadSession(
	String uploadId,
	Long uploadedBy,
	FilePurpose purpose,
	Long recordId,
	Long roomId,
	UploadStatus status,
	String bucket,
	String tmpKey,
	String confirmedKey,
	String contentType,
	long sizeBytes,
	Instant uploadAcceptedAt,
	Instant uploadExpiresAt,
	Long imageId,
	String materialId
) {

	public static final String TMP_PREFIX = "tmp/";

	/** API-012. 임시 경로는 {@code tmp/{uploadId}} 다. */
	public static UploadSession start(String uploadId, Long uploadedBy, FilePurpose purpose, Long recordId,
		Long roomId, String bucket, String contentType, long sizeBytes, Instant acceptedAt, Instant expiresAt) {
		return new UploadSession(uploadId, uploadedBy, purpose, recordId, roomId, UploadStatus.UPLOADING, bucket,
			TMP_PREFIX + uploadId, null, contentType, sizeBytes, acceptedAt, expiresAt, null, null);
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(uploadExpiresAt);
	}
}
