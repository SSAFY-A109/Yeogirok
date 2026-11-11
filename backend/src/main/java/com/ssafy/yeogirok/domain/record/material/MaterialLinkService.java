package com.ssafy.yeogirok.domain.record.material;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.file.session.UploadStatus;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkResponse;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-062 영수증·결제 자료 연결(명세 {@code specs/record-api-2.md} 4-3). 트랜잭션 밖에서 돈다(RDB 를 쓰지 않고, OCR 작업은
 * 자료를 저장한 뒤 맡긴다).
 */
@Service
@RequiredArgsConstructor
public class MaterialLinkService {

	private final RecordFinder recordFinder;
	private final ReceiptMaterials materials;
	private final ReceiptMaterialStore materialStore;
	private final UploadSessionStore uploadSessionStore;
	private final ReceiptOcrRunner ocrRunner;
	private final Clock clock;

	public MaterialLinkResponse link(Long userId, Long recordId, MaterialLinkCommand command) {
		recordFinder.checkWritable(recordId, userId);
		String materialId = ReceiptMaterial.idOf(command.uploadId());
		String jobId = ocrRunner.newJobId();
		Instant now = clock.instant();
		ReceiptMaterial linked = materials.update(materialId, material -> {
			checkOwner(material, userId, recordId);
			if (material.isLinked()) {
				throw new BusinessException(ExceptionType.MATERIAL_ALREADY_LINKED);
			}
			if (!material.isOriginalAvailable(now)) {
				throw new BusinessException(ExceptionType.FILE_NOT_FOUND, "영수증 원본이 삭제됐거나 보관 기한이 지났습니다.");
			}
			// 목록에 먼저 넣는다. 자료 저장이 실패해도 063 은 연결된 자료만 보여 준다.
			materialStore.addToIndex(recordId, userId, materialId);
			return material.linked(command.type().name(), jobId, now);
		}).orElseThrow(() -> missingReason(command.uploadId(), userId));
		ocrRunner.submit(materialId, jobId, linked);
		return MaterialLinkResponse.queued(materialId, recordId, jobId);
	}

	/** 같은 멱등키 재전송. 권한을 다시 확인하고 지금 자료의 jobId 로 처음과 같은 모양을 돌려준다. */
	public MaterialLinkResponse getLinked(Long userId, Long recordId, String materialId) {
		recordFinder.checkWritable(recordId, userId);
		ReceiptMaterial material = materials.find(materialId)
			.orElseThrow(() -> new BusinessException(ExceptionType.MATERIAL_NOT_FOUND));
		checkOwner(material, userId, recordId);
		return MaterialLinkResponse.queued(materialId, recordId, material.jobId());
	}

	private static void checkOwner(ReceiptMaterial material, Long userId, Long recordId) {
		if (!userId.equals(material.uploadedBy()) || !recordId.equals(material.recordId())) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
	}

	/** 자료가 없을 때 업로드 세션으로 이유를 가린다. 세션이 만료됐으면 M004. */
	private BusinessException missingReason(String uploadId, Long userId) {
		UploadSession session = uploadSessionStore.find(uploadId).orElse(null);
		if (session == null) {
			return new BusinessException(ExceptionType.FILE_NOT_FOUND);
		}
		if (!session.uploadedBy().equals(userId)) {
			return new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
		if (session.purpose() != FilePurpose.RECEIPT) {
			return new BusinessException(ExceptionType.FILE_PURPOSE_MISMATCH);
		}
		if (session.status() != UploadStatus.READY) {
			return new BusinessException(ExceptionType.FILE_NOT_UPLOADED);
		}
		return new BusinessException(ExceptionType.FILE_NOT_FOUND);
	}
}
