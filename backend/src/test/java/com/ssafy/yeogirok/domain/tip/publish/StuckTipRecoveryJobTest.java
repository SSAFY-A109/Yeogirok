package com.ssafy.yeogirok.domain.tip.publish;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registeringAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.dto.TipRetryRequest.TipRetryCommand;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.domain.tip.service.TipPhotoClaims;
import com.ssafy.yeogirok.domain.tip.service.MyTipFinder;
import com.ssafy.yeogirok.domain.tip.service.PublicTipFinder;
import com.ssafy.yeogirok.domain.tip.service.TipPhotoUploads;
import com.ssafy.yeogirok.domain.tip.service.TipPlacements;
import com.ssafy.yeogirok.domain.tip.service.TipRelocations;
import com.ssafy.yeogirok.domain.tip.service.TipRetryService;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.InMemoryTipEditStore;
import com.ssafy.yeogirok.support.InMemoryTipPhotoClaims;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.InMemoryUploadSessions;
import com.ssafy.yeogirok.support.MutableClock;

import tools.jackson.databind.json.JsonMapper;

/** 게시가 끊긴 팁 정리 → 051 재시도 → 게시까지. 기준 시간은 공통 설정 5분, 시계는 등록 6분 뒤. */
@DataJpaTest
@RecordApplicationEvents
@Import({StuckTipRecoveryJob.class, TipPublisher.class, TipRetryService.class, TipEditor.class, MyTipFinder.class, PublicTipFinder.class,
	TipPhotoUploads.class, TipRelocations.class, TipPlacements.class, LocationEvidencePolicy.class,
	StuckTipRecoveryJobTest.Config.class})
class StuckTipRecoveryJobTest {

	static final Long AUTHOR = 15001L;
	static final InMemoryTipPhotoClaims CLAIMS = new InMemoryTipPhotoClaims();
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();
	static final InMemoryTipEditStore EDITS = new InMemoryTipEditStore();
	static final InMemoryUploadSessions SESSIONS = new InMemoryUploadSessions();

	@TestConfiguration
	@EnableConfigurationProperties({TipProperties.class, LocationEvidenceProperties.class})
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW.plus(Duration.ofMinutes(6)));
		}

		@Bean
		JsonMapper jsonMapper() {
			return JsonMapper.builder().build();
		}

		@Bean
		UploadSessionStore uploadSessionStore() {
			return SESSIONS.store;
		}

		@Bean
		TipPhotoClaims tipPhotoClaims() {
			return CLAIMS.store;
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
	StuckTipRecoveryJob job;

	@Autowired
	TipRetryService retryService;

	@Autowired
	TipPublisher publisher;

	@Autowired
	TestEntityManager em;

	@Autowired
	ApplicationEvents events;

	@BeforeEach
	void clear() {
		STATES.states.clear();
		CLAIMS.claims.clear();
		EDITS.clear();
	}

	@Test
	void 게시_작업_없이_오래된_REGISTERING_팁은_FAILED이고_작업도_재시도_가능한_실패가_된다() {
		Long tipId = em.persistAndGetId(registering(AUTHOR), Long.class);
		STATES.states.put(tipId, TipPublishState.queued("job-1", "tip-photo/a").running());

		job.recover();

		assertThat(statusOf(tipId)).isEqualTo(TipStatus.FAILED);
		TipPublishState state = STATES.states.get(tipId);
		assertThat(state.jobId()).isEqualTo("job-1");
		assertThat(state.imagePath()).isEqualTo("tip-photo/a");
		assertThat(state.operationStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(state.error().code()).isEqualTo("C014");
		assertThat(state.retryable()).isTrue();
	}

	@Test
	void 기준_시간_안의_팁과_게시된_팁과_삭제된_팁은_건드리지_않는다() {
		Long fresh = em.persistAndGetId(registeringAt(AUTHOR, NOW.plus(Duration.ofMinutes(2))), Long.class);
		Long publishedId = em.persistAndGetId(published(AUTHOR), Long.class);
		ArTip deleted = registering(AUTHOR);
		deleted.delete(NOW);
		Long deletedId = em.persistAndGetId(deleted, Long.class);

		job.recover();

		assertThat(statusOf(fresh)).isEqualTo(TipStatus.REGISTERING);
		assertThat(statusOf(publishedId)).isEqualTo(TipStatus.PUBLISHED);
		assertThat(statusOf(deletedId)).isEqualTo(TipStatus.DELETED);
	}

	@Test
	void Redis_작업_정보가_없으면_팁만_FAILED로_돌린다() {
		Long tipId = em.persistAndGetId(registering(AUTHOR), Long.class);

		job.recover();

		assertThat(statusOf(tipId)).isEqualTo(TipStatus.FAILED);
		assertThat(STATES.states).doesNotContainKey(tipId);
	}

	@Test
	void 정리한_뒤_051로_재시도하면_같은_사진으로_게시된다() {
		Long tipId = em.persistAndGetId(registering(AUTHOR), Long.class);
		STATES.states.put(tipId, TipPublishState.queued("job-1", "tip-photo/a"));
		job.recover();

		retryService.retry(AUTHOR, tipId, new TipRetryCommand(null, null, null, null));
		publisher.publish(events.stream(TipPublishRequested.class).findFirst().orElseThrow());

		assertThat(statusOf(tipId)).isEqualTo(TipStatus.PUBLISHED);
		assertThat(em.getEntityManager()
			.createQuery("select i.imagePath from ArTipImage i where i.tipId = :tipId", String.class)
			.setParameter("tipId", tipId).getResultList()).containsExactly("tip-photo/a");
		assertThat(STATES.states.get(tipId).operationStatus()).isEqualTo(OperationStatus.SUCCEEDED);
	}

	private TipStatus statusOf(Long tipId) {
		em.flush();
		em.clear();
		return em.find(ArTip.class, tipId).getStatus();
	}
}
