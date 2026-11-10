package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.tip.dto.TipDeleteResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishRequested;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;
import com.ssafy.yeogirok.domain.tip.publish.TipPublisher;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.MutableClock;

/** API-055 규칙. 삭제 뒤 게시 작업이 와도 되살아나지 않는지 실제 TipPublisher 로 본다. */
@DataJpaTest
@Import({TipDeleteService.class, TipPublisher.class, TipDeleteServiceTest.Config.class})
class TipDeleteServiceTest {

	static final Long AUTHOR = 10001L;
	static final Long OTHER = 10002L;

	@TestConfiguration
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW.plusSeconds(60));
		}

		@Bean
		TipPublishStore tipPublishStore() {
			return new InMemoryTipPublishStates().store;
		}
	}

	@Autowired
	TipDeleteService service;

	@Autowired
	TipPublisher publisher;

	@Autowired
	TestEntityManager em;

	@Test
	void 삭제하면_DELETED이고_삭제_시각과_version이_바뀐다() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));
		int version = saved.getVersion();

		TipDeleteResponse response = service.delete(AUTHOR, saved.getId(), version);
		em.flush();
		em.clear();

		ArTip tip = em.find(ArTip.class, saved.getId());
		assertThat(response).isEqualTo(new TipDeleteResponse(saved.getId(), TipStatus.DELETED));
		assertThat(tip.getStatus()).isEqualTo(TipStatus.DELETED);
		assertThat(tip.getDeletedAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(tip.getVersion()).isEqualTo(version + 1);
	}

	@Test
	void 이미_삭제했으면_version과_상관없이_같은_결과이고_version을_다시_올리지_않는다() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));
		service.delete(AUTHOR, saved.getId(), saved.getVersion());
		em.flush();
		int deletedVersion = em.find(ArTip.class, saved.getId()).getVersion();

		TipDeleteResponse again = service.delete(AUTHOR, saved.getId(), saved.getVersion());
		em.flush();
		em.clear();

		assertThat(again).isEqualTo(new TipDeleteResponse(saved.getId(), TipStatus.DELETED));
		assertThat(em.find(ArTip.class, saved.getId()).getVersion()).isEqualTo(deletedVersion);
	}

	@Test
	void version이_다르면_C004_남의_팁이면_T002_없으면_T012() {
		ArTip saved = em.persistFlushFind(published(AUTHOR));

		expect(AUTHOR, saved.getId(), saved.getVersion() + 1, ExceptionType.VERSION_MISMATCH);
		expect(OTHER, saved.getId(), saved.getVersion(), ExceptionType.TIP_NOT_OWNER);
		expect(AUTHOR, saved.getId() + 1000, 1, ExceptionType.TIP_NOT_EXIST);
	}

	@Test
	void 게시_전에_삭제한_팁은_게시_작업이_와도_되살아나지_않는다() {
		ArTip saved = em.persistFlushFind(registering(AUTHOR));
		service.delete(AUTHOR, saved.getId(), saved.getVersion());
		em.flush();

		publisher.publish(new TipPublishRequested(saved.getId(), "job-1", null));
		em.flush();
		em.clear();

		ArTip tip = em.find(ArTip.class, saved.getId());
		assertThat(tip.getStatus()).isEqualTo(TipStatus.DELETED);
		assertThat(tip.getPublishedAt()).isNull();
	}

	private void expect(Long userId, Long tipId, int version, ExceptionType type) {
		assertThatThrownBy(() -> service.delete(userId, tipId, version)).extracting("exceptionType").isEqualTo(type);
	}
}
