package com.ssafy.yeogirok.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ssafy.yeogirok.domain.file.FilePolicy;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.dto.UploadCompleteResponse;
import com.ssafy.yeogirok.domain.file.session.ReceiptCleanupQueue;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.session.UploadLock;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadStatus;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.file.storage.StoredObject;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;
import com.ssafy.yeogirok.domain.record.service.RecordPhotoService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.InMemoryUploadSessions;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;

/** API-086 규칙: 처리 순서·용도별 저장·검증 실패 시 FAILED·재시도 가능한 실패·READY 재호출. S3·Redis 는 대신한다. */
class UploadCompleteServiceTest {

	static final Long USER_ID = 7L;
	static final Long RECORD_ID = 100L;
	static final String UPLOAD_ID = "550e8400-e29b-41d4-a716-446655440010";
	static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
	static final String ETAG = "\"etag-1\"";
	static final byte[] PNG = {(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
	static final byte[] JPEG = {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};

	final InMemoryUploadSessions memory = new InMemoryUploadSessions();
	final UploadLock lock = mock(UploadLock.class);
	final FileStorage storage = mock(FileStorage.class);
	final RecordAccess recordAccess = mock(RecordAccess.class);
	final RecordPhotoService recordPhotoService = mock(RecordPhotoService.class);
	final ReceiptMaterialStore materialStore = mock(ReceiptMaterialStore.class);
	final ReceiptCleanupQueue cleanupQueue = mock(ReceiptCleanupQueue.class);
	MutableClock clock;
	UploadCompleteService service;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(NOW);
		given(lock.acquire(UPLOAD_ID)).willReturn("token");
		given(recordAccess.isCurrentMember(RECORD_ID, USER_ID)).willReturn(true);
		given(recordPhotoService.saveUploaded(eq(RECORD_ID), eq(USER_ID), anyString())).willReturn(801L);
		service = new UploadCompleteService(memory.store, lock, storage, new FilePolicy(TestFileProperties.VALUE),
			TestFileProperties.VALUE, recordAccess, recordPhotoService, materialStore, cleanupQueue, clock);
	}

	/** 012 가 만든 것과 같은 세션. 사진은 photos, 영수증은 receipts 버킷. */
	void uploading(FilePurpose purpose, long sizeBytes) {
		memory.put(UploadSession.start(UPLOAD_ID, USER_ID, purpose, purpose.recordRequired() ? RECORD_ID : null, null,
			TestFileProperties.VALUE.bucketFor(purpose), "image/png", sizeBytes, NOW,
			NOW.plus(Duration.ofMinutes(10))));
	}

	/** 앱이 tmp key 에 올린 실물. 복사본도 같은 크기·ETag 다. */
	void uploaded(long sizeBytes, byte[] header) {
		given(storage.head(anyString(), eq("tmp/" + UPLOAD_ID)))
			.willReturn(Optional.of(new StoredObject(sizeBytes, ETAG)));
		given(storage.head(anyString(), eq(session().purpose().confirmedKey(UPLOAD_ID))))
			.willReturn(Optional.of(new StoredObject(sizeBytes, ETAG)));
		given(storage.readHeader(anyString(), eq("tmp/" + UPLOAD_ID), anyInt(), eq(ETAG))).willReturn(header);
	}

	UploadSession session() {
		return memory.sessions.get(UPLOAD_ID);
	}

	@Test
	void 팁_사진은_검증한_ETag_로만_확정_경로에_복사하고_세션만_READY_로_둔다() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		uploaded(1024, PNG);

		UploadCompleteResponse response = service.complete(USER_ID, UPLOAD_ID);

		assertThat(response).isEqualTo(new UploadCompleteResponse(UPLOAD_ID, UploadStatus.READY, null, null,
			"image/png", 1024));
		verify(storage).copyIfMatch("photos", "tmp/" + UPLOAD_ID, "tip-photo/" + UPLOAD_ID, ETAG);
		assertThat(session().confirmedKey()).isEqualTo("tip-photo/" + UPLOAD_ID);
		verifyNoInteractions(recordPhotoService, materialStore, cleanupQueue);
		verify(lock).release(UPLOAD_ID, "token");
	}

	@Test
	void 기록_사진은_보관함에_미연결로_저장하고_imageId_를_준다() {
		uploading(FilePurpose.RECORD_PHOTO, 2048);
		uploaded(2048, PNG);

		UploadCompleteResponse response = service.complete(USER_ID, UPLOAD_ID);

		assertThat(response.imageId()).isEqualTo(801L);
		assertThat(response.materialId()).isNull();
		verify(recordPhotoService).saveUploaded(RECORD_ID, USER_ID, "record-photo/" + UPLOAD_ID);
	}

	@Test
	void 영수증은_확정_원본도_최초_발급_24시간_뒤_정리_대상으로_등록하고_자료를_만든다() {
		uploading(FilePurpose.RECEIPT, 4096);
		uploaded(4096, PNG);
		clock.advance(Duration.ofMinutes(5));

		UploadCompleteResponse response = service.complete(USER_ID, UPLOAD_ID);

		Instant deleteAfter = NOW.plus(Duration.ofHours(24));
		verify(cleanupQueue).register("receipts", "receipt/" + UPLOAD_ID, deleteAfter);
		verify(materialStore).save(response.materialId(), ReceiptMaterial.uploaded(RECORD_ID, USER_ID,
			"receipt/" + UPLOAD_ID, NOW, deleteAfter));
		assertThat(response.materialId()).isNotEqualTo(UPLOAD_ID).hasSize(36);
		assertThat(response.imageId()).isNull();
	}

	@Test
	void 영수증_자료_ID_는_다시_처리해도_같다() {
		uploading(FilePurpose.RECEIPT, 4096);
		uploaded(4096, PNG);
		String first = service.complete(USER_ID, UPLOAD_ID).materialId();
		uploading(FilePurpose.RECEIPT, 4096);

		assertThat(service.complete(USER_ID, UPLOAD_ID).materialId()).isEqualTo(first);
	}

	@Test
	void READY_재호출은_만료_뒤에도_잠금_없이_처음_결과를_준다() {
		uploading(FilePurpose.RECORD_PHOTO, 2048);
		uploaded(2048, PNG);
		UploadCompleteResponse first = service.complete(USER_ID, UPLOAD_ID);
		clock.advance(Duration.ofHours(1));

		assertThat(service.complete(USER_ID, UPLOAD_ID)).isEqualTo(first);
		verify(lock).acquire(UPLOAD_ID);
		verify(recordPhotoService).saveUploaded(any(), any(), any());
	}

	@Test
	void 세션이_없으면_M004_다른_사람_것이면_M005() {
		assertError(() -> service.complete(USER_ID, UPLOAD_ID), ExceptionType.FILE_NOT_FOUND);
		uploading(FilePurpose.TIP_PHOTO, 1024);
		assertError(() -> service.complete(99L, UPLOAD_ID), ExceptionType.FILE_ACCESS_DENIED);
	}

	@Test
	void 미완료_세션이_만료됐으면_M011() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		clock.advance(Duration.ofMinutes(10));

		assertError(() -> service.complete(USER_ID, UPLOAD_ID), ExceptionType.UPLOAD_SESSION_EXPIRED);
		verifyNoInteractions(storage);
	}

	@Test
	void 그사이_기록에서_나갔으면_M008() {
		uploading(FilePurpose.RECEIPT, 4096);
		given(recordAccess.isCurrentMember(RECORD_ID, USER_ID)).willReturn(false);

		assertError(() -> service.complete(USER_ID, UPLOAD_ID), ExceptionType.UPLOAD_TARGET_ACCESS_DENIED);
		verifyNoInteractions(storage);
	}

	@Test
	void 아직_올라오지_않았으면_M006_이고_세션은_그대로_다시_시도할_수_있다() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		given(storage.head(anyString(), anyString())).willReturn(Optional.empty());

		assertError(() -> service.complete(USER_ID, UPLOAD_ID), ExceptionType.FILE_NOT_UPLOADED);
		assertThat(session().status()).isEqualTo(UploadStatus.UPLOADING);
		verify(lock).release(UPLOAD_ID, "token");
	}

	@Test
	void 저장소_장애는_M012_이고_세션은_그대로다() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		given(storage.head(anyString(), anyString()))
			.willThrow(new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE));

		assertError(() -> service.complete(USER_ID, UPLOAD_ID), ExceptionType.FILE_STORAGE_UNAVAILABLE);
		assertThat(session().status()).isEqualTo(UploadStatus.UPLOADING);
	}

	@Test
	void 실물이_한도를_넘으면_M001() {
		uploading(FilePurpose.RECEIPT, 10L * 1_048_576 + 1);
		uploaded(10L * 1_048_576 + 1, PNG);

		assertFailed(ExceptionType.FILE_TOO_LARGE);
	}

	@Test
	void 실물_크기가_신고와_다르면_M003() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		uploaded(1025, PNG);

		assertFailed(ExceptionType.FILE_VERIFICATION_FAILED);
	}

	@Test
	void 앞부분이_JPEG_PNG_WEBP_가_아니면_M002() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		uploaded(1024, new byte[] {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0});

		assertFailed(ExceptionType.FILE_TYPE_NOT_SUPPORTED);
	}

	@Test
	void PNG_로_신고하고_JPEG_를_올리면_M003() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		uploaded(1024, JPEG);

		assertFailed(ExceptionType.FILE_VERIFICATION_FAILED);
	}

	@Test
	void 검증하는_사이_다시_올라와_ETag_조건이_깨지면_M003() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		uploaded(1024, PNG);
		willThrow(new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED))
			.given(storage).copyIfMatch(any(), any(), any(), any());

		assertFailed(ExceptionType.FILE_VERIFICATION_FAILED);
	}

	@Test
	void 확정_복사본이_검증한_객체와_다르면_M003() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		uploaded(1024, PNG);
		given(storage.head(anyString(), eq("tip-photo/" + UPLOAD_ID)))
			.willReturn(Optional.of(new StoredObject(1024, "\"other\"")));

		assertFailed(ExceptionType.FILE_VERIFICATION_FAILED);
	}

	@ParameterizedTest
	@EnumSource(value = FilePurpose.class)
	void 검증에_실패한_세션은_다시_불러도_M003_이고_S3_를_부르지_않는다(FilePurpose purpose) {
		uploading(purpose, 1024);
		memory.put(session().failed());

		assertError(() -> service.complete(USER_ID, UPLOAD_ID), ExceptionType.FILE_VERIFICATION_FAILED);
		verifyNoInteractions(storage);
	}

	@Test
	void 같은_uploadId_를_다른_요청이_처리_중이면_C018() {
		uploading(FilePurpose.TIP_PHOTO, 1024);
		given(lock.acquire(UPLOAD_ID)).willThrow(new BusinessException(ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS));

		assertError(() -> service.complete(USER_ID, UPLOAD_ID), ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS);
		verifyNoInteractions(storage);
		verify(lock, never()).release(any(), any());
	}

	/** 같은 파일로는 다시 통과할 수 없는 실패: 세션을 FAILED 로 두고 확정 저장은 하지 않는다. */
	private void assertFailed(ExceptionType expected) {
		assertError(() -> service.complete(USER_ID, UPLOAD_ID), expected);
		assertThat(session().status()).isEqualTo(UploadStatus.FAILED);
		verifyNoInteractions(recordPhotoService, materialStore);
		verify(lock).release(UPLOAD_ID, "token");
	}

	private static void assertError(Runnable call, ExceptionType expected) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getExceptionType()).isEqualTo(expected));
	}
}
