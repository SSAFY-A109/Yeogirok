package com.ssafy.yeogirok.domain.file.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.FilePolicy;
import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.ImageFormat;
import com.ssafy.yeogirok.domain.file.dto.UploadCompleteResponse;
import com.ssafy.yeogirok.domain.file.session.ReceiptCleanupQueue;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.session.UploadLock;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.file.session.UploadStatus;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.file.storage.StoredObject;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.domain.record.service.RecordPhotoService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-086 업로드 완료 확인. 앱이 올린 tmp 객체를 실물로 검증하고, 검증한 ETag 의 객체만 확정 경로로 복사한 뒤 용도별로 저장한다.
 * 처리 순서는 specs/file-api.md 4-3. 멱등키는 컨트롤러가 이 밖에서 처리하고, uploadId 별로는 {@link UploadLock} 으로 한 줄로 세운다.
 * S3 호출이 길어 트랜잭션을 걸지 않는다(사진 저장만 {@link RecordPhotoService} 트랜잭션).
 */
@Service
@RequiredArgsConstructor
public class UploadCompleteService {

	/** 같은 파일로는 다시 시도해도 통과하지 못하는 실패. 세션을 FAILED 로 두고 새 012 세션으로 올리게 한다. */
	private static final Set<ExceptionType> VERIFICATION_FAILURES = EnumSet.of(ExceptionType.FILE_TOO_LARGE,
		ExceptionType.FILE_TYPE_NOT_SUPPORTED, ExceptionType.FILE_VERIFICATION_FAILED);

	private final UploadSessionStore sessionStore;
	private final UploadLock uploadLock;
	private final FileStorage fileStorage;
	private final FilePolicy filePolicy;
	private final FileProperties properties;
	private final RecordAccess recordAccess;
	private final RecordPhotoService recordPhotoService;
	private final ReceiptMaterialStore materialStore;
	private final ReceiptCleanupQueue receiptCleanupQueue;
	private final Clock clock;

	/** 이미 READY 면 만료와 상관없이 처음 결과를 돌려준다. */
	public UploadCompleteResponse complete(Long userId, String uploadId) {
		UploadSession session = findOwned(userId, uploadId);
		if (session.status() == UploadStatus.READY) {
			return UploadCompleteResponse.of(session);
		}
		String lockToken = uploadLock.acquire(uploadId);
		try {
			// 잠금을 기다리는 동안 다른 요청이 끝냈을 수 있다.
			session = findOwned(userId, uploadId);
			if (session.status() == UploadStatus.READY) {
				return UploadCompleteResponse.of(session);
			}
			return UploadCompleteResponse.of(confirm(session));
		} finally {
			uploadLock.release(uploadId, lockToken);
		}
	}

	private UploadSession confirm(UploadSession session) {
		if (session.status() == UploadStatus.FAILED) {
			throw new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED);
		}
		if (session.isExpired(clock.instant())) {
			throw new BusinessException(ExceptionType.UPLOAD_SESSION_EXPIRED);
		}
		if (session.purpose().recordRequired() && !recordAccess.isCurrentMember(session.recordId(),
			session.uploadedBy())) {
			throw new BusinessException(ExceptionType.UPLOAD_TARGET_ACCESS_DENIED);
		}
		StoredObject uploaded = fileStorage.head(session.bucket(), session.tmpKey())
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_UPLOADED));

		String confirmedKey = session.purpose().confirmedKey(session.uploadId());
		try {
			verifyAndCopy(session, uploaded, confirmedKey);
		} catch (BusinessException e) {
			if (VERIFICATION_FAILURES.contains(e.getExceptionType())) {
				sessionStore.save(session.failed());
			}
			throw e;
		}

		UploadSession ready = storeByPurpose(session, confirmedKey, uploaded.sizeBytes());
		sessionStore.save(ready);
		return ready;
	}

	/** 크기 M001 → 요청한 크기와 다름 M003 → 형식 M002 → 요청한 형식과 다름 M003 → 같은 ETag 일 때만 복사 → 복사본 재확인. */
	private void verifyAndCopy(UploadSession session, StoredObject uploaded, String confirmedKey) {
		filePolicy.requireSize(session.purpose(), uploaded.sizeBytes());
		if (uploaded.sizeBytes() != session.sizeBytes()) {
			throw new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED);
		}
		byte[] header = fileStorage.readHeader(session.bucket(), session.tmpKey(), ImageFormat.HEADER_BYTES,
			uploaded.eTag());
		ImageFormat format = ImageFormat.detect(header)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_TYPE_NOT_SUPPORTED));
		if (!format.mimeType().equals(session.contentType())) {
			throw new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED);
		}

		fileStorage.copyIfMatch(session.bucket(), session.tmpKey(), confirmedKey, uploaded.eTag());
		StoredObject confirmed = fileStorage.head(session.bucket(), confirmedKey)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_UPLOADED));
		// SSE-S3 라 복사해도 ETag 가 같다. SSE-KMS 로 바꾸면 ETag 가 달라지므로 이 비교를 바꿔야 한다.
		if (confirmed.sizeBytes() != uploaded.sizeBytes() || !confirmed.eTag().equals(uploaded.eTag())) {
			throw new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED);
		}
	}

	private UploadSession storeByPurpose(UploadSession session, String confirmedKey, long sizeBytes) {
		if (session.purpose() == FilePurpose.RECORD_PHOTO) {
			Long imageId = recordPhotoService.saveUploaded(session.recordId(), session.uploadedBy(), confirmedKey);
			return session.ready(confirmedKey, sizeBytes, imageId, null);
		}
		if (session.purpose() == FilePurpose.RECEIPT) {
			Instant deleteAfter = session.uploadAcceptedAt().plus(properties.receiptRetention());
			receiptCleanupQueue.register(session.bucket(), confirmedKey, deleteAfter);
			String materialId = ReceiptMaterial.idOf(session.uploadId());
			materialStore.save(materialId, ReceiptMaterial.uploaded(session.recordId(), session.uploadedBy(),
				confirmedKey, session.uploadAcceptedAt(), deleteAfter));
			return session.ready(confirmedKey, sizeBytes, null, materialId);
		}
		// TIP_PHOTO 는 팁에 연결할 때 ar_tip_images 에 저장한다(AR 팁 API).
		return session.ready(confirmedKey, sizeBytes, null, null);
	}

	/** 세션이 없으면 M004, 다른 사람 세션이면 M005. */
	private UploadSession findOwned(Long userId, String uploadId) {
		UploadSession session = sessionStore.find(uploadId)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		if (!session.uploadedBy().equals(userId)) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
		return session;
	}
}
