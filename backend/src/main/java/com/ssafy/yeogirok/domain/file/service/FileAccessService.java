package com.ssafy.yeogirok.domain.file.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.dto.FileAccessResponse;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-013 파일 조회 URL. 요청마다 권한·소속을 다시 확인하고 확정 객체에만 GET URL 을 발급한다.
 * 기록 사진은 현재 기록 참여자, 영수증 원본은 현재 기록 참여자이면서 업로더 본인만. 팁 사진은 AR 팁 API(TipPhotoUrls)에서 같은 {@link FileUrlIssuer} 로 발급한다.
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

	private final RecordAccess recordAccess;
	private final VisitImageRepository visitImageRepository;
	private final ReceiptMaterialStore materialStore;
	private final FileUrlIssuer fileUrlIssuer;
	private final FileProperties properties;
	private final Clock clock;

	/** 참여자가 아니면 M005. 사진이 없거나 다른 기록의 사진이면 M004(다른 기록 사진 ID 의 존재를 드러내지 않는다). */
	public FileAccessResponse recordPhoto(Long userId, Long recordId, Long imageId) {
		requireRecordMember(recordId, userId);
		VisitImage image = visitImageRepository.findById(imageId)
			.filter(found -> found.getRecordId().equals(recordId))
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		Instant now = clock.instant();
		return fileUrlIssuer.issue(properties.bucketFor(FilePurpose.RECORD_PHOTO), image.getImagePath(), now,
			properties.downloadUrlTtl());
	}

	/**
	 * 참여자가 아니거나 업로더가 아니면 M005. 자료가 없거나, 다른 기록의 자료이거나, 원본을 지웠거나, 삭제 기한이 지났으면 M004.
	 * URL 만료는 삭제 기한을 넘기지 않는다.
	 */
	public FileAccessResponse receipt(Long userId, Long recordId, String materialId) {
		requireRecordMember(recordId, userId);
		ReceiptMaterial material = materialStore.find(materialId)
			.filter(found -> found.recordId().equals(recordId))
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		if (!material.uploadedBy().equals(userId)) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
		Instant now = clock.instant();
		if (!material.isOriginalAvailable(now)) {
			throw new BusinessException(ExceptionType.FILE_NOT_FOUND);
		}
		Duration untilDelete = Duration.between(now, material.deleteAfter());
		// Presigned URL 은 초 단위(최소 1초)로 서명된다. 내림해 expiresAt 을 실제 만료와 맞추고, 1초 미만이면 기한으로 본다.
		Duration validFor = (untilDelete.compareTo(properties.downloadUrlTtl()) < 0
			? untilDelete : properties.downloadUrlTtl()).truncatedTo(ChronoUnit.SECONDS);
		if (validFor.isZero()) {
			throw new BusinessException(ExceptionType.FILE_NOT_FOUND);
		}
		return fileUrlIssuer.issue(properties.receiptBucket(), material.objectKey(), now, validFor);
	}

	private void requireRecordMember(Long recordId, Long userId) {
		if (!recordAccess.isCurrentMember(recordId, userId)) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
	}
}
