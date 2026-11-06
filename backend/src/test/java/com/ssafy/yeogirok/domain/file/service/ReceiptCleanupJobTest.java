package com.ssafy.yeogirok.domain.file.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.file.session.ReceiptCleanupQueue;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;

/** 영수증 원본 정리(AC-01~04): 기한 지난 것만, 삭제 확인 뒤에만 목록에서 빼고, 실패는 남겨 재시도, 버킷 안전망 스캔. */
class ReceiptCleanupJobTest {

	static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
	static final String UPLOAD_A = "550e8400-e29b-41d4-a716-44665544000a";
	static final String UPLOAD_B = "550e8400-e29b-41d4-a716-44665544000b";

	final ReceiptCleanupQueue queue = mock(ReceiptCleanupQueue.class);
	final ReceiptMaterialStore materialStore = mock(ReceiptMaterialStore.class);
	final FileStorage storage = mock(FileStorage.class);
	ReceiptCleanupJob job;

	@BeforeEach
	void setUp() {
		job = new ReceiptCleanupJob(queue, materialStore, storage, TestFileProperties.VALUE, new MutableClock(NOW));
	}

	@Test
	void 기한_지난_원본을_지운_뒤_목록에서_빼고_확정_원본이면_자료를_DELETED_로_표시한다() {
		given(queue.due(NOW)).willReturn(new LinkedHashSet<>(List.of("receipts/tmp/" + UPLOAD_A,
			"receipts/receipt/" + UPLOAD_A)));

		job.deleteDue();

		verify(storage).delete("receipts", "tmp/" + UPLOAD_A);
		verify(storage).delete("receipts", "receipt/" + UPLOAD_A);
		verify(queue).remove("receipts/tmp/" + UPLOAD_A);
		verify(queue).remove("receipts/receipt/" + UPLOAD_A);
		// tmp 원본에는 자료가 없다. 확정 원본만 자료를 찾는다.
		verify(materialStore).markOriginalDeleted(ReceiptMaterial.idOf(UPLOAD_A));
		verify(materialStore).markOriginalDeleted(any());
	}

	@Test
	void 기한_전_항목은_목록이_주지_않으므로_지우지_않는다() {
		given(queue.due(NOW)).willReturn(new LinkedHashSet<>());

		job.deleteDue();

		verifyNoInteractions(storage, materialStore);
	}

	@Test
	void S3_삭제가_실패하면_목록에_남기고_다음_항목은_계속_지운다() {
		given(queue.due(NOW)).willReturn(new LinkedHashSet<>(List.of("receipts/receipt/" + UPLOAD_A,
			"receipts/receipt/" + UPLOAD_B)));
		willThrow(new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE))
			.given(storage).delete("receipts", "receipt/" + UPLOAD_A);

		job.deleteDue();

		verify(queue, never()).remove("receipts/receipt/" + UPLOAD_A);
		verify(materialStore, never()).markOriginalDeleted(ReceiptMaterial.idOf(UPLOAD_A));
		verify(queue).remove("receipts/receipt/" + UPLOAD_B);
		verify(materialStore).markOriginalDeleted(ReceiptMaterial.idOf(UPLOAD_B));
	}

	@Test
	void 안전망은_영수증_버킷에서_보관_기간이_지난_객체를_지운다() {
		given(storage.listKeysModifiedBefore("receipts", NOW.minus(Duration.ofHours(24))))
			.willReturn(List.of("tmp/" + UPLOAD_A, "receipt/" + UPLOAD_B));

		job.deleteExpiredInBucket();

		verify(storage).delete("receipts", "tmp/" + UPLOAD_A);
		verify(storage).delete("receipts", "receipt/" + UPLOAD_B);
		verify(materialStore).markOriginalDeleted(ReceiptMaterial.idOf(UPLOAD_B));
	}

	@Test
	void 안전망_스캔이_실패해도_예외를_밖으로_던지지_않는다() {
		given(storage.listKeysModifiedBefore(eq("receipts"), any()))
			.willThrow(new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE));

		job.deleteExpiredInBucket();

		verify(storage, never()).delete(any(), any());
	}
}
