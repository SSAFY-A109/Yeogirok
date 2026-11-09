package com.ssafy.yeogirok.domain.tip.publish;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft.PhotoChange;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.support.InMemoryTipEditStore;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;

/** 팁에 연결되지 않은 사진 정리. S3 는 가짜, ar_tip_images 는 H2, Redis 는 맵. */
@DataJpaTest
@Import({TipPhotoCleanupJob.class, TipPhotoCleanupJobTest.Config.class})
class TipPhotoCleanupJobTest {

	static final FileStorage STORAGE = mock(FileStorage.class);
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();
	static final InMemoryTipEditStore EDITS = new InMemoryTipEditStore();

	@TestConfiguration
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}

		@Bean
		FileStorage fileStorage() {
			return STORAGE;
		}

		@Bean
		FileProperties fileProperties() {
			return TestFileProperties.VALUE;
		}

		@Bean
		IdempotencyProperties idempotencyProperties() {
			return new IdempotencyProperties(Duration.ofHours(24), Duration.ofSeconds(30));
		}

		@Bean
		TipPublishStore tipPublishStore() {
			return STATES.store;
		}

		@Bean
		TipEditStore tipEditStore() {
			return EDITS.store;
		}
	}

	@Autowired
	TipPhotoCleanupJob job;

	@Autowired
	TestEntityManager em;

	@BeforeEach
	void clear() {
		reset(STORAGE);
		STATES.states.clear();
		EDITS.clear();
	}

	@Test
	void 확정_24시간이_지났고_어디에도_연결되지_않은_사진만_지운다() {
		Long tipId = em.persistAndGetId(published(1L), Long.class);
		em.persist(ArTipImage.of(tipId, "tip-photo/linked", NOW));
		STATES.states.put(2L, TipPublishState.queued("job-1", "tip-photo/publishing").running()
			.failed("C014", "x", true));
		EDITS.drafts.put("3:job-2", new TipEditDraft(1L, 1, null, null, PhotoChange.REPLACE, "tip-photo/editing", null));
		given(STORAGE.listKeysModifiedBefore("photos", "tip-photo/", NOW.minus(Duration.ofHours(24)))).willReturn(List.of(
			"tip-photo/linked", "tip-photo/publishing", "tip-photo/editing", "tip-photo/orphan"));

		job.deleteUnlinked();

		verify(STORAGE).delete("photos", "tip-photo/orphan");
		verify(STORAGE, never()).delete("photos", "tip-photo/linked");
		verify(STORAGE, never()).delete("photos", "tip-photo/publishing");
		verify(STORAGE, never()).delete("photos", "tip-photo/editing");
	}

	@Test
	void 오래된_사진이_없으면_아무것도_지우지_않는다() {
		given(STORAGE.listKeysModifiedBefore(anyString(), anyString(), any())).willReturn(List.of());

		job.deleteUnlinked();

		verify(STORAGE, never()).delete(anyString(), anyString());
	}

	@Test
	void 저장소_장애는_다음_주기로_미루고_예외를_밖으로_내지_않는다() {
		given(STORAGE.listKeysModifiedBefore(anyString(), anyString(), any()))
			.willThrow(new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE));

		assertThatCode(() -> job.deleteUnlinked()).doesNotThrowAnyException();
	}
}
