package com.ssafy.yeogirok.domain.file.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.dto.FileAccessResponse;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.file.storage.StoredObject;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-013 파일 조회 URL. 요청마다 권한·소속을 다시 확인하고 확정 객체에만 GET URL 을 발급한다.
 * 기록 사진은 현재 기록 참여자, 영수증 원본은 현재 기록 참여자이면서 업로더 본인만. 팁 사진은 AR 팁 API 에서 연결한다.
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

	private final RecordAccess recordAccess;
	private final VisitImageRepository visitImageRepository;
	private final ReceiptMaterialStore materialStore;
	private final FileStorage fileStorage;
	private final FileProperties properties;
	private final Clock clock;

	/** 참여자가 아니거나 다른 기록의 사진이면 M005, 사진이 없으면 M004. */
	public FileAccessResponse recordPhoto(Long userId, Long recordId, Long imageId) {
		requireRecordMember(recordId, userId);
		VisitImage image = visitImageRepository.findById(imageId)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		if (!image.getRecordId().equals(recordId)) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
		Instant now = clock.instant();
		return issue(properties.bucketFor(FilePurpose.RECORD_PHOTO), image.getImagePath(), now,
			properties.downloadUrlTtl());
	}

	/**
	 * 참여자가 아니거나, 다른 기록의 자료이거나, 업로더가 아니면 M005. 자료가 없거나 원본을 지웠거나 삭제 기한이 지났으면 M004.
	 * URL 만료는 삭제 기한을 넘기지 않는다.
	 */
	public FileAccessResponse receipt(Long userId, Long recordId, String materialId) {
		requireRecordMember(recordId, userId);
		ReceiptMaterial material = materialStore.find(materialId)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		if (!material.recordId().equals(recordId) || !material.uploadedBy().equals(userId)) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
		Instant now = clock.instant();
		if (!material.isOriginalAvailable(now)) {
			throw new BusinessException(ExceptionType.FILE_NOT_FOUND);
		}
		Duration untilDelete = Duration.between(now, material.deleteAfter());
		Duration validFor = untilDelete.compareTo(properties.downloadUrlTtl()) < 0
			? untilDelete : properties.downloadUrlTtl();
		return issue(properties.receiptBucket(), material.objectKey(), now, validFor);
	}

	private void requireRecordMember(Long recordId, Long userId) {
		if (!recordAccess.isCurrentMember(recordId, userId)) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
	}

	/** Content-Type 은 업로드 때 서명으로 고정한 객체 값을 쓴다(visit_images 에는 형식 컬럼이 없다). 객체가 없으면 M004. */
	private FileAccessResponse issue(String bucket, String key, Instant now, Duration validFor) {
		StoredObject object = fileStorage.head(bucket, key)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		return new FileAccessResponse(fileStorage.presignGet(bucket, key, validFor), now.plus(validFor),
			object.contentType());
	}
}
