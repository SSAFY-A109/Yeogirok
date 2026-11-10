package com.ssafy.yeogirok.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;

/** API-013 규칙: 참여자·소속·업로더 확인, 영수증 URL 은 삭제 기한을 넘지 않고 기한 뒤 M004. */
class FileAccessServiceTest {

	static final Long USER_ID = 7L;
	static final Long RECORD_ID = 100L;
	static final Long IMAGE_ID = 801L;
	static final String MATERIAL_ID = "6f1c2a9e-0000-3000-8000-000000000001";
	static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
	static final Instant ACCEPTED = NOW.minus(Duration.ofHours(1));

	final RecordAccess recordAccess = mock(RecordAccess.class);
	final VisitImageRepository visitImageRepository = mock(VisitImageRepository.class);
	final ReceiptMaterialStore materialStore = mock(ReceiptMaterialStore.class);
	final FileStorage storage = mock(FileStorage.class);
	MutableClock clock;
	FileAccessService service;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(NOW);
		given(recordAccess.isCurrentMember(RECORD_ID, USER_ID)).willReturn(true);
		given(storage.head(anyString(), anyString())).willReturn(Optional.of(new StoredObject(10, "\"e\"", "image/png")));
		given(storage.presignGet(anyString(), anyString(), any())).willReturn("https://signed-get");
		service = new FileAccessService(recordAccess, visitImageRepository, materialStore,
			new FileUrlIssuer(storage), TestFileProperties.VALUE, clock);
	}

	void photo(Long recordId) {
		VisitImage image = VisitImage.unlinked(recordId, 9L, "record-photo/abc", NOW);
		ReflectionTestUtils.setField(image, "id", IMAGE_ID);
		given(visitImageRepository.findById(IMAGE_ID)).willReturn(Optional.of(image));
	}

	void receipt(ReceiptMaterial material) {
		given(materialStore.find(MATERIAL_ID)).willReturn(Optional.of(material));
	}

	static ReceiptMaterial uploadedBy(Long uploader) {
		return ReceiptMaterial.uploaded(RECORD_ID, uploader, "receipt/abc", ACCEPTED, ACCEPTED.plus(Duration.ofHours(24)));
	}

	/* ── 기록 사진 ─────────────────────────────────────────────── */

	@Test
	void 기록_참여자는_다른_참여자가_올린_사진도_15분짜리_URL_로_받는다() {
		photo(RECORD_ID);

		FileAccessResponse response = service.recordPhoto(USER_ID, RECORD_ID, IMAGE_ID);

		assertThat(response).isEqualTo(new FileAccessResponse("https://signed-get", NOW.plus(Duration.ofMinutes(15)),
			"image/png"));
		verify(storage).presignGet("photos", "record-photo/abc", Duration.ofMinutes(15));
	}

	@Test
	void 기록_참여자가_아니면_M005_이고_사진을_찾지도_않는다() {
		assertError(() -> service.recordPhoto(99L, RECORD_ID, IMAGE_ID), ExceptionType.FILE_ACCESS_DENIED);
		verifyNoInteractions(visitImageRepository, storage);
	}

	@Test
	void 다른_기록의_사진도_없는_사진과_같이_M004() {
		photo(RECORD_ID + 1);
		assertError(() -> service.recordPhoto(USER_ID, RECORD_ID, IMAGE_ID), ExceptionType.FILE_NOT_FOUND);
		assertError(() -> service.recordPhoto(USER_ID, RECORD_ID, IMAGE_ID + 1), ExceptionType.FILE_NOT_FOUND);
	}

	@Test
	void 저장소에_객체가_없으면_M004() {
		photo(RECORD_ID);
		given(storage.head(anyString(), anyString())).willReturn(Optional.empty());

		assertError(() -> service.recordPhoto(USER_ID, RECORD_ID, IMAGE_ID), ExceptionType.FILE_NOT_FOUND);
	}

	/* ── 영수증 원본 ───────────────────────────────────────────── */

	@Test
	void 업로더_본인은_영수증_원본을_영수증_버킷에서_받는다() {
		receipt(uploadedBy(USER_ID));

		FileAccessResponse response = service.receipt(USER_ID, RECORD_ID, MATERIAL_ID);

		assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		verify(storage).presignGet("receipts", "receipt/abc", Duration.ofMinutes(15));
	}

	@Test
	void 삭제_기한이_15분보다_가까우면_URL_도_기한에_맞춰_끝난다() {
		receipt(uploadedBy(USER_ID));
		clock.set(ACCEPTED.plus(Duration.ofHours(24)).minus(Duration.ofMinutes(5)));

		FileAccessResponse response = service.receipt(USER_ID, RECORD_ID, MATERIAL_ID);

		assertThat(response.expiresAt()).isEqualTo(ACCEPTED.plus(Duration.ofHours(24)));
		verify(storage).presignGet("receipts", "receipt/abc", Duration.ofMinutes(5));
	}

	@Test
	void URL_수명은_초_단위로_내림하고_1초_미만이면_M004() {
		receipt(uploadedBy(USER_ID));
		Instant deleteAfter = ACCEPTED.plus(Duration.ofHours(24));
		clock.set(deleteAfter.minusMillis(1500));

		FileAccessResponse response = service.receipt(USER_ID, RECORD_ID, MATERIAL_ID);

		assertThat(response.expiresAt()).isEqualTo(deleteAfter.minusMillis(500));
		verify(storage).presignGet("receipts", "receipt/abc", Duration.ofSeconds(1));

		clock.set(deleteAfter.minusMillis(999));
		assertError(() -> service.receipt(USER_ID, RECORD_ID, MATERIAL_ID), ExceptionType.FILE_NOT_FOUND);
	}

	@Test
	void 삭제_기한이_되면_물리_삭제_전이어도_M004() {
		receipt(uploadedBy(USER_ID));
		clock.set(ACCEPTED.plus(Duration.ofHours(24)));

		assertError(() -> service.receipt(USER_ID, RECORD_ID, MATERIAL_ID), ExceptionType.FILE_NOT_FOUND);
		verifyNoInteractions(storage);
	}

	@Test
	void 원본을_지운_자료는_M004() {
		ReceiptMaterial live = uploadedBy(USER_ID);
		receipt(new ReceiptMaterial(null, RECORD_ID, USER_ID, "RECEIPT", null, "READY", "DELETED", live.deleteAfter(),
			ACCEPTED));

		assertError(() -> service.receipt(USER_ID, RECORD_ID, MATERIAL_ID), ExceptionType.FILE_NOT_FOUND);
	}

	@Test
	void 같은_기록_참여자라도_업로더가_아니면_M005() {
		receipt(uploadedBy(9L));

		assertError(() -> service.receipt(USER_ID, RECORD_ID, MATERIAL_ID), ExceptionType.FILE_ACCESS_DENIED);
	}

	@Test
	void 다른_기록의_자료도_없는_자료와_같이_M004() {
		given(recordAccess.isCurrentMember(RECORD_ID + 1, USER_ID)).willReturn(true);
		receipt(uploadedBy(USER_ID));
		assertError(() -> service.receipt(USER_ID, RECORD_ID + 1, MATERIAL_ID), ExceptionType.FILE_NOT_FOUND);

		given(materialStore.find(MATERIAL_ID)).willReturn(Optional.empty());
		assertError(() -> service.receipt(USER_ID, RECORD_ID, MATERIAL_ID), ExceptionType.FILE_NOT_FOUND);
	}

	@Test
	void 업로더라도_기록에서_나갔으면_M005() {
		receipt(uploadedBy(USER_ID));
		given(recordAccess.isCurrentMember(RECORD_ID, USER_ID)).willReturn(false);

		assertError(() -> service.receipt(USER_ID, RECORD_ID, MATERIAL_ID), ExceptionType.FILE_ACCESS_DENIED);
	}

	private static void assertError(Runnable call, ExceptionType expected) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getExceptionType()).isEqualTo(expected));
	}
}
