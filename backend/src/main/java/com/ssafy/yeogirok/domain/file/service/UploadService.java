package com.ssafy.yeogirok.domain.file.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.FilePolicy;
import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateCommand;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateResponse;
import com.ssafy.yeogirok.domain.file.session.ReceiptCleanupQueue;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.file.session.UploadStatus;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** API-012 업로드 URL 발급. 멱등키는 컨트롤러가 이 밖에서 처리한다. */
@Service
@RequiredArgsConstructor
public class UploadService {

	private final FilePolicy filePolicy;
	private final FileProperties properties;
	private final UploadSessionStore sessionStore;
	private final FileStorage fileStorage;
	private final ReceiptCleanupQueue receiptCleanupQueue;
	private final RecordAccess recordAccess;
	private final Clock clock;

	/**
	 * 형식 M002·크기 M001·기록 참여(M008)를 보고, 세션을 저장한 뒤 tmp key 에만 올릴 수 있는 URL 을 발급한다.
	 * 영수증은 URL 을 주기 전에 원본 정리 대상으로 먼저 등록한다(못 하면 M012).
	 */
	public UploadCreateResponse create(Long userId, UploadCreateCommand command) {
		String contentType = filePolicy.requireFormat(command.contentType()).mimeType();
		filePolicy.requireSize(command.purpose(), command.sizeBytes());
		requireRecordMember(command.purpose().recordRequired(), command.recordId(), userId);

		Instant now = clock.instant();
		UploadSession session = UploadSession.start(UUID.randomUUID().toString(), userId, command.purpose(),
			command.recordId(), command.roomId(), properties.bucketFor(command.purpose()), contentType,
			command.sizeBytes(), now, now.plus(properties.uploadUrlTtl()));
		if (command.purpose().isReceipt()) {
			receiptCleanupQueue.register(session.bucket(), session.tmpKey(), now.plus(properties.receiptRetention()));
		}
		sessionStore.save(session);
		return UploadCreateResponse.of(session, presign(session, properties.uploadUrlTtl()));
	}

	/**
	 * 같은 멱등키로 다시 보낸 요청. 아직 올릴 수 있으면 같은 uploadId·key 로 남은 시간만큼 URL 을 다시 서명한다.
	 * 끝난 세션(READY·FAILED)은 M013, 만료된 미완료 세션은 M011. 그사이 기록에서 나갔으면 M008.
	 */
	public UploadCreateResponse getCreated(Long userId, String uploadId) {
		UploadSession session = sessionStore.find(uploadId)
			.filter(found -> found.uploadedBy().equals(userId))
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		if (session.status() != UploadStatus.UPLOADING) {
			throw new BusinessException(ExceptionType.UPLOAD_ALREADY_COMPLETED);
		}
		Instant now = clock.instant();
		if (session.isExpired(now)) {
			throw new BusinessException(ExceptionType.UPLOAD_SESSION_EXPIRED);
		}
		requireRecordMember(session.purpose().recordRequired(), session.recordId(), userId);
		return UploadCreateResponse.of(session, presign(session, Duration.between(now, session.uploadExpiresAt())));
	}

	private void requireRecordMember(boolean required, Long recordId, Long userId) {
		if (required && !recordAccess.isCurrentMember(recordId, userId)) {
			throw new BusinessException(ExceptionType.UPLOAD_TARGET_ACCESS_DENIED);
		}
	}

	private String presign(UploadSession session, Duration validFor) {
		return fileStorage.presignPut(session.bucket(), session.tmpKey(), session.contentType(), validFor);
	}
}
