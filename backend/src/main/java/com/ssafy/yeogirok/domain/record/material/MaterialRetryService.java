package com.ssafy.yeogirok.domain.record.material;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.record.dto.MaterialRetryResponse;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-092 영수증 OCR 개별 재시도(명세 {@code specs/record-api-2.md} 4-6). OCR 제안·사용자 확정 값은 그대로 두고 새 작업을 맡긴다.
 * 늦게 끝난 이전 작업은 jobId 가 달라 자료를 바꾸지 못한다(ReceiptOcrRunner).
 */
@Service
@RequiredArgsConstructor
public class MaterialRetryService {

	private final RecordFinder recordFinder;
	private final ReceiptMaterials materials;
	private final ReceiptOcrRunner ocrRunner;
	private final Clock clock;

	/** 판정 순서: RE001/RE003 → RE008 → M005 → RE022 → C004 → 원본 없음·만료 M004. */
	public MaterialRetryResponse retry(Long userId, Long recordId, String materialId, int expectedVersion) {
		recordFinder.checkWritable(recordId, userId);
		String jobId = ocrRunner.newJobId();
		Instant now = clock.instant();
		ReceiptMaterial queued = materials.update(materialId, material -> {
			materials.checkChangeable(material, recordId, userId, expectedVersion);
			if (!material.isOriginalAvailable(now)) {
				throw new BusinessException(ExceptionType.FILE_NOT_FOUND, "영수증 원본이 삭제됐거나 보관 기한이 지났습니다.");
			}
			return material.retryQueued(jobId);
		}).orElseThrow(() -> new BusinessException(ExceptionType.MATERIAL_NOT_FOUND));
		ocrRunner.submit(materialId, jobId, queued);
		return MaterialRetryResponse.queued(materialId, queued.version(), jobId);
	}

	/** 같은 멱등키 재전송. 권한을 다시 확인하고 처음 맡긴 jobId 와 지금 자료 version 으로 답한다(명세 1-5). */
	public MaterialRetryResponse getRetried(Long userId, Long recordId, String materialId, String jobId) {
		recordFinder.checkWritable(recordId, userId);
		ReceiptMaterial material = materials.find(materialId)
			.orElseThrow(() -> new BusinessException(ExceptionType.MATERIAL_NOT_FOUND));
		ReceiptMaterials.checkOwnLinked(material, recordId, userId);
		return MaterialRetryResponse.queued(materialId, material.version(), jobId);
	}
}
