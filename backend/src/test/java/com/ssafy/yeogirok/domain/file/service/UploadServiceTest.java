package com.ssafy.yeogirok.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import com.ssafy.yeogirok.domain.file.FilePolicy;
import com.ssafy.yeogirok.domain.file.FilePurpose;
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
import com.ssafy.yeogirok.support.InMemoryUploadSessions;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;

/** API-012 규칙: 세션 내용, 형식·크기·기록 참여 검사, 영수증 정리 대상 등록, 같은 멱등키 재요청(재서명·M011·M013). */
class UploadServiceTest {

	static final Long USER_ID = 7L;
	static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
	static final UploadCreateCommand TIP = new UploadCreateCommand(FilePurpose.TIP_PHOTO, "a.png", "IMAGE/PNG",
		1024, null, null);
	static final Long RECORD_ID = 100L;
	static final UploadCreateCommand RECEIPT = new UploadCreateCommand(FilePurpose.RECEIPT, "r.jpg", "image/jpeg",
		2048, null, RECORD_ID);

	final InMemoryUploadSessions memory = new InMemoryUploadSessions();
	final Map<String, UploadSession> sessions = memory.sessions;
	final UploadSessionStore store = memory.store;
	final FileStorage storage = mock(FileStorage.class);
	final ReceiptCleanupQueue cleanupQueue = mock(ReceiptCleanupQueue.class);
	final RecordAccess recordAccess = mock(RecordAccess.class);
	MutableClock clock;
	UploadService service;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(NOW);
		given(storage.presignPut(anyString(), anyString(), anyString(), any())).willReturn("https://signed");
		given(recordAccess.isCurrentMember(RECORD_ID, USER_ID)).willReturn(true);
		service = new UploadService(new FilePolicy(TestFileProperties.VALUE), TestFileProperties.VALUE, store,
			storage, cleanupQueue, recordAccess, clock);
	}

	@Test
	void 발급하면_UPLOADING_세션을_저장하고_tmp_key_에_10분짜리_URL_을_준다() {
		UploadCreateResponse response = service.create(USER_ID, TIP);

		UploadSession saved = sessions.get(response.uploadId());
		assertThat(saved.status()).isEqualTo(UploadStatus.UPLOADING);
		assertThat(saved.bucket()).isEqualTo("photos");
		assertThat(saved.tmpKey()).isEqualTo("tmp/" + response.uploadId());
		assertThat(saved.uploadAcceptedAt()).isEqualTo(NOW);
		assertThat(saved.uploadExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		// MIME 은 표준 표기로 저장·서명한다.
		assertThat(response.contentType()).isEqualTo("image/png");
		assertThat(response.uploadHeaders()).containsEntry("Content-Type", "image/png");
		assertThat(response.uploadMethod()).isEqualTo("PUT");
		assertThat(response.uploadUrl()).isEqualTo("https://signed");
		verify(storage).presignPut("photos", saved.tmpKey(), "image/png", Duration.ofMinutes(10));
	}

	@Test
	void 형식_M002_크기_M001_이면_세션을_만들지_않는다() {
		assertError(() -> service.create(USER_ID,
			new UploadCreateCommand(FilePurpose.TIP_PHOTO, "a.heic", "image/heic", 1024, null, null)),
			ExceptionType.FILE_TYPE_NOT_SUPPORTED);
		assertError(() -> service.create(USER_ID,
			new UploadCreateCommand(FilePurpose.TIP_PHOTO, "a.png", "image/png", 20L * 1_048_576 + 1, null, null)),
			ExceptionType.FILE_TOO_LARGE);
		verify(store, never()).save(any());
	}

	@Test
	void 영수증은_영수증_버킷에_올리고_URL_전에_tmp_key_를_24시간_뒤_정리_대상으로_등록한다() {
		UploadCreateResponse response = service.create(USER_ID, RECEIPT);

		UploadSession saved = sessions.get(response.uploadId());
		assertThat(saved.bucket()).isEqualTo("receipts");
		assertThat(saved.recordId()).isEqualTo(RECORD_ID);
		InOrder order = inOrder(cleanupQueue, store, storage);
		order.verify(cleanupQueue).register("receipts", saved.tmpKey(), NOW.plus(Duration.ofHours(24)));
		order.verify(store).save(saved);
		order.verify(storage).presignPut(any(), any(), any(), any());
	}

	@Test
	void 사진은_정리_대상으로_등록하지_않는다() {
		service.create(USER_ID, TIP);
		service.create(USER_ID, new UploadCreateCommand(FilePurpose.RECORD_PHOTO, "a.png", "image/png", 1, null,
			RECORD_ID));

		verifyNoInteractions(cleanupQueue);
	}

	@Test
	void 정리_대상_등록이_실패하면_M012_이고_세션도_URL_도_만들지_않는다() {
		willThrow(new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE))
			.given(cleanupQueue).register(any(), any(), any());

		assertError(() -> service.create(USER_ID, RECEIPT), ExceptionType.FILE_STORAGE_UNAVAILABLE);
		verify(store, never()).save(any());
		verifyNoInteractions(storage);
	}

	@ParameterizedTest
	@EnumSource(value = FilePurpose.class, names = {"RECORD_PHOTO", "RECEIPT"})
	void 기록_참여자가_아니면_M008(FilePurpose purpose) {
		assertError(() -> service.create(99L, new UploadCreateCommand(purpose, "a.png", "image/png", 1, null,
			RECORD_ID)), ExceptionType.UPLOAD_TARGET_ACCESS_DENIED);
		verify(store, never()).save(any());
	}

	@Test
	void 팁_사진은_기록_참여를_보지_않는다() {
		service.create(USER_ID, TIP);

		verifyNoInteractions(recordAccess);
	}

	@Test
	void 재요청_사이에_기록에서_나갔으면_M008() {
		String uploadId = service.create(USER_ID, RECEIPT).uploadId();
		given(recordAccess.isCurrentMember(RECORD_ID, USER_ID)).willReturn(false);

		assertError(() -> service.getCreated(USER_ID, uploadId), ExceptionType.UPLOAD_TARGET_ACCESS_DENIED);
	}

	@Test
	void 재요청이_만료_전이면_같은_uploadId_로_남은_시간만큼_다시_서명한다() {
		String uploadId = service.create(USER_ID, TIP).uploadId();
		clock.advance(Duration.ofMinutes(4));

		UploadCreateResponse again = service.getCreated(USER_ID, uploadId);

		assertThat(again.uploadId()).isEqualTo(uploadId);
		assertThat(again.uploadExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		verify(storage).presignPut(eq("photos"), eq("tmp/" + uploadId), eq("image/png"), eq(Duration.ofMinutes(6)));
	}

	@Test
	void 재요청이_만료_뒤면_M011() {
		String uploadId = service.create(USER_ID, TIP).uploadId();
		clock.advance(Duration.ofMinutes(10));

		assertError(() -> service.getCreated(USER_ID, uploadId), ExceptionType.UPLOAD_SESSION_EXPIRED);
	}

	@ParameterizedTest
	@EnumSource(value = UploadStatus.class, names = {"READY", "FAILED"})
	void 끝난_세션의_재요청은_만료_전이어도_M013(UploadStatus status) {
		String uploadId = service.create(USER_ID, TIP).uploadId();
		UploadSession uploading = sessions.get(uploadId);
		memory.put(status == UploadStatus.READY ? uploading.ready("tip-photo/" + uploadId, 1024, null, null)
			: uploading.failed());

		assertError(() -> service.getCreated(USER_ID, uploadId), ExceptionType.UPLOAD_ALREADY_COMPLETED);
	}

	@Test
	void 세션이_없거나_다른_사람_것이면_M004() {
		String uploadId = service.create(USER_ID, TIP).uploadId();

		assertError(() -> service.getCreated(99L, uploadId), ExceptionType.FILE_NOT_FOUND);
		assertError(() -> service.getCreated(USER_ID, "missing"), ExceptionType.FILE_NOT_FOUND);
	}

	private static void assertError(Runnable call, ExceptionType expected) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getExceptionType()).isEqualTo(expected));
	}
}
