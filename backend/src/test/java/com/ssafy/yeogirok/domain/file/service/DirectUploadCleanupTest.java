package com.ssafy.yeogirok.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.file.session.ReceiptCleanupQueue;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.tip.publish.TipEditStore;
import com.ssafy.yeogirok.domain.tip.publish.TipPhotoCleanupJob;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;
import com.ssafy.yeogirok.domain.tip.repository.ArTipImageRepository;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.support.InMemoryFileStorage;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;

/**
 * 서버가 직접 올린 기록 사진({@code record-photo/})을 주인 없는 파일 정리 작업이 지우지 않는다(명세 tour-course-seed.md 6-1,
 * AC-03). 두 정리 작업을 실제로 돌리고, 정리 대상인 팁 사진은 지워지는 것으로 작업이 돌았음을 함께 확인한다.
 */
class DirectUploadCleanupTest {

	static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
	static final String RECORD_PHOTO = "record-photo/seed-1";
	static final String ORPHAN_TIP_PHOTO = "tip-photo/orphan";

	final InMemoryFileStorage storage = new InMemoryFileStorage(NOW.minus(Duration.ofDays(30)));

	@Test
	void 정리_작업을_돌려도_직접_올린_기록_사진은_남는다() {
		storage.put("photos", RECORD_PHOTO, new byte[] {1, 2, 3}, "image/jpeg");
		storage.put("photos", ORPHAN_TIP_PHOTO, new byte[] {9}, "image/jpeg");
		ArTipImageRepository tipImages = mock(ArTipImageRepository.class);
		given(tipImages.findLinkedPaths(anyList())).willReturn(List.of());
		TipPublishStore publishStore = mock(TipPublishStore.class);
		given(publishStore.imagePaths()).willReturn(Set.of());
		TipEditStore editStore = mock(TipEditStore.class);
		given(editStore.imagePaths()).willReturn(Set.of());

		new TipPhotoCleanupJob(storage, TestFileProperties.VALUE, tipImages, publishStore, editStore,
			new IdempotencyProperties(Duration.ofHours(24), Duration.ofSeconds(30)), new MutableClock(NOW))
			.deleteUnlinked();
		new ReceiptCleanupJob(mock(ReceiptCleanupQueue.class), mock(ReceiptMaterialStore.class), storage,
			TestFileProperties.VALUE, RecordTestConfig.RECORD_PROPERTIES, new MutableClock(NOW))
			.deleteExpiredInBucket();

		assertThat(storage.keys("photos")).containsExactly(RECORD_PHOTO);
	}
}
