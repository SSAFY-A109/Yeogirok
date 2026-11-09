package com.ssafy.yeogirok.domain.tip.publish;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft.PhotoChange;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.support.InMemoryTipEditStore;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.MutableClock;

/**
 * API-054 비동기 적용이 실패하거나 지난 작업인 경우. 팁은 그대로 두고 작업만 FAILED, 예약은 푼다.
 * 성공 경우는 TipUpdateServiceTest 가 본다.
 */
@DataJpaTest
@Import({TipEditor.class, TipEditorTest.Config.class})
class TipEditorTest {

	static final Long AUTHOR = 12001L;
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();
	static final InMemoryTipEditStore EDITS = new InMemoryTipEditStore();

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

		@Bean
		TipEditStore tipEditStore() {
			return EDITS.store;
		}
	}

	@Autowired
	TipEditor editor;

	@Autowired
	TestEntityManager em;

	@BeforeEach
	void clear() {
		STATES.states.clear();
		EDITS.clear();
	}

	@Test
	void 요청_뒤_version이_바뀌었으면_C004로_실패하고_다시_시도할_수_없다() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		String token = reserveAndSave(tip.getId(), draft(tip.getVersion()));
		tip.edit(null, "다른 수정이 먼저 반영됨", NOW);
		em.flush();

		editor.apply(tip.getId(), "job-1", token);

		TipPublishState state = STATES.states.get(tip.getId());
		assertThat(state.operationStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(state.error().code()).isEqualTo("C004");
		assertThat(state.retryable()).isFalse();
		em.clear();
		assertThat(em.find(ArTip.class, tip.getId()).getCategory()).isEqualTo(ArTipCategory.CAFE);
		assertThat(EDITS.locks).isEmpty();
	}

	@Test
	void 요청_뒤_삭제됐으면_T012로_실패하고_되살리지_않는다() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		String token = reserveAndSave(tip.getId(), draft(tip.getVersion()));
		tip.delete(NOW);
		em.flush();

		editor.apply(tip.getId(), "job-1", token);

		assertThat(STATES.states.get(tip.getId()).error().code()).isEqualTo("T012");
		em.clear();
		ArTip after = em.find(ArTip.class, tip.getId());
		assertThat(after.isDeleted()).isTrue();
		assertThat(after.getCategory()).isEqualTo(ArTipCategory.CAFE);
	}

	@Test
	void 수정안이_없으면_실패로_남기고_예약을_푼다() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		String token = EDITS.store.reserve(tip.getId()).orElseThrow();
		STATES.states.put(tip.getId(), TipPublishState.queuedUpdate("job-1", false, false));

		editor.apply(tip.getId(), "job-1", token);

		TipPublishState state = STATES.states.get(tip.getId());
		assertThat(state.operationStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(state.retryable()).isFalse();
		assertThat(EDITS.locks).isEmpty();
	}

	@Test
	void 다음_수정이_새_작업을_만든_뒤_늦게_돈_작업은_팁도_새_작업_상태도_건드리지_않는다() {
		ArTip tip = em.persistFlushFind(published(AUTHOR));
		String token = reserveAndSave(tip.getId(), draft(tip.getVersion()));
		TipPublishState newer = TipPublishState.queuedUpdate("job-2", false, false);
		STATES.states.put(tip.getId(), newer);

		editor.apply(tip.getId(), "job-1", token);

		assertThat(STATES.states.get(tip.getId())).isEqualTo(newer);
		assertThat(EDITS.drafts).isEmpty();
		em.clear();
		ArTip after = em.find(ArTip.class, tip.getId());
		assertThat(after.getCategory()).isEqualTo(ArTipCategory.CAFE);
		assertThat(after.getVersion()).isEqualTo(tip.getVersion());
	}

	/** 054 가 하는 것처럼 예약을 잡고 수정안과 QUEUED 작업 상태를 저장한다. */
	private String reserveAndSave(Long tipId, TipEditDraft draft) {
		String token = EDITS.store.reserve(tipId).orElseThrow();
		EDITS.store.save(tipId, "job-1", draft);
		STATES.states.put(tipId, TipPublishState.queuedUpdate("job-1", false, false));
		return token;
	}

	private static TipEditDraft draft(int baseVersion) {
		return new TipEditDraft(AUTHOR, baseVersion, ArTipCategory.TMI, null, PhotoChange.KEEP, null, null);
	}
}
