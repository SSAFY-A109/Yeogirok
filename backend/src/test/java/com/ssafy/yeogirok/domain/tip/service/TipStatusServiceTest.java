package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static com.ssafy.yeogirok.domain.tip.publish.TipPublishState.StepStatus.SKIPPED;
import static com.ssafy.yeogirok.domain.tip.publish.TipPublishState.StepStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.tip.dto.TipStatusResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishRequested;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Operation;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Steps;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;
import com.ssafy.yeogirok.domain.tip.publish.TipPublisher;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.MutableClock;

/** API-050 규칙. 게시 전후 상태는 실제 TipPublisher 로 바꾼다. */
@DataJpaTest
@Import({TipStatusService.class, MyTipFinder.class, TipPublisher.class, TipStatusServiceTest.Config.class})
class TipStatusServiceTest {

	static final Long AUTHOR = 6001L;
	static final Long OTHER = 6002L;
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();

	@TestConfiguration
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}

		@Bean
		TipPublishStore tipPublishStore() {
			return STATES.store;
		}
	}

	@Autowired
	TipStatusService service;

	@Autowired
	TipPublisher publisher;

	@Autowired
	TestEntityManager em;

	@BeforeEach
	void clear() {
		STATES.states.clear();
	}

	@Test
	void 등록_직후는_진행_중이고_게시_후에는_PUBLISHED와_SUCCEEDED() {
		Long tipId = em.persistAndGetId(registering(AUTHOR), Long.class);
		STATES.states.put(tipId, TipPublishState.queued("job-1", null));

		TipStatusResponse queued = service.getStatus(AUTHOR, tipId);
		assertThat(queued.status()).isEqualTo(TipStatus.REGISTERING);
		assertThat(queued.operation()).isEqualTo(Operation.CREATE);
		assertThat(queued.operationStatus()).isEqualTo(OperationStatus.QUEUED);
		assertThat(queued.jobId()).isEqualTo("job-1");

		publisher.publish(new TipPublishRequested(tipId, "job-1", null));
		em.flush();
		em.clear();

		TipStatusResponse published = service.getStatus(AUTHOR, tipId);
		assertThat(published.status()).isEqualTo(TipStatus.PUBLISHED);
		assertThat(published.operationStatus()).isEqualTo(OperationStatus.SUCCEEDED);
		assertThat(published.steps()).isEqualTo(new Steps(SKIPPED, SUCCEEDED, SUCCEEDED));
		assertThat(published.error()).isNull();
		assertThat(published.retryable()).isFalse();
		assertThat(published.version()).isEqualTo(em.find(ArTip.class, tipId).getVersion());
	}

	@Test
	void 실패하면_FAILED와_operationStatus_FAILED() {
		ArTip tip = registering(AUTHOR);
		tip.fail(NOW);
		Long tipId = em.persistAndGetId(tip, Long.class);
		STATES.states.put(tipId, TipPublishState.queued("job-1", "tip-photo/x").running()
			.failed("C014", "실패", true));

		TipStatusResponse response = service.getStatus(AUTHOR, tipId);

		assertThat(response.status()).isEqualTo(TipStatus.FAILED);
		assertThat(response.operationStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(response.steps().media()).isEqualTo(TipPublishState.StepStatus.FAILED);
		assertThat(response.error()).isEqualTo(new TipPublishState.Error("C014", "실패"));
		assertThat(response.retryable()).isTrue();
	}

	@Test
	void Redis에_작업_정보가_없으면_작업_필드는_null이고_팁_상태만_준다() {
		Long tipId = em.persistAndGetId(registering(AUTHOR), Long.class);

		TipStatusResponse response = service.getStatus(AUTHOR, tipId);

		assertThat(response.status()).isEqualTo(TipStatus.REGISTERING);
		assertThat(response.jobId()).isNull();
		assertThat(response.operation()).isNull();
		assertThat(response.operationStatus()).isNull();
		assertThat(response.steps()).isEqualTo(new Steps(null, null, null));
		assertThat(response.retryable()).isFalse();
	}

	@Test
	void 남의_팁은_T002_없거나_삭제된_팁은_T012() {
		Long tipId = em.persistAndGetId(registering(AUTHOR), Long.class);
		Long deleted = em.persistAndGetId(registering(AUTHOR), Long.class);
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET deleted_at = created_at WHERE id = " + deleted)
			.executeUpdate();

		expect(OTHER, tipId, ExceptionType.TIP_NOT_OWNER);
		expect(AUTHOR, deleted, ExceptionType.TIP_NOT_EXIST);
		expect(AUTHOR, tipId + 1000, ExceptionType.TIP_NOT_EXIST);
	}

	private void expect(Long userId, Long tipId, ExceptionType type) {
		assertThatThrownBy(() -> service.getStatus(userId, tipId)).extracting("exceptionType").isEqualTo(type);
	}
}
