package com.ssafy.yeogirok.domain.tip.publish;

import static com.ssafy.yeogirok.domain.tip.TipFixture.LAT;
import static com.ssafy.yeogirok.domain.tip.TipFixture.LNG;
import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.locationAt;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchRequest.TipSearchQuery;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.domain.tip.repository.ArTipImageRepository;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.domain.tip.service.TipSearchService;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.support.InMemoryTipPublishStates;
import com.ssafy.yeogirok.support.MutableClock;

/** 049 커밋 뒤 게시. 이벤트 연결(@Async·AFTER_COMMIT)은 스프링이 하고, 여기서는 publish 를 직접 부른다. */
@DataJpaTest
@Import({TipPublisher.class, TipSearchService.class, LocationEvidencePolicy.class, TipPublisherTest.Config.class})
class TipPublisherTest {

	static final Long AUTHOR = 4001L;
	static final InMemoryTipPublishStates STATES = new InMemoryTipPublishStates();

	@TestConfiguration
	@EnableConfigurationProperties({TipProperties.class, LocationEvidenceProperties.class})
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
	TipPublisher publisher;

	@Autowired
	TipSearchService searchService;

	@Autowired
	TestEntityManager em;

	@BeforeEach
	void clear() {
		STATES.states.clear();
	}

	@Test
	void 사진_없이_게시하면_PUBLISHED이고_주변_탐색에_보인다() {
		Long tipId = em.persistAndGetId(registering(AUTHOR), Long.class);

		publisher.publish(new TipPublishRequested(tipId, "job-1", null));

		em.flush();
		em.clear();
		ArTip tip = em.find(ArTip.class, tipId);
		assertThat(tip.getStatus()).isEqualTo(TipStatus.PUBLISHED);
		assertThat(tip.getPublishedAt()).isEqualTo(NOW);
		assertThat(STATES.states.get(tipId)).isEqualTo(TipPublishState.queued("job-1", null).running().succeeded());
		assertThat(searchService.search(new TipSearchQuery(locationAt(LAT, LNG), null, null, null)).items())
			.extracting(item -> item.id()).containsExactly(tipId);
	}

	@Test
	void 사진이_있으면_ar_tip_images에_연결하고_media_SUCCEEDED() {
		Long tipId = em.persistAndGetId(registering(AUTHOR), Long.class);

		publisher.publish(new TipPublishRequested(tipId, "job-1", "tip-photo/abc"));

		assertThat(em.getEntityManager()
			.createQuery("select i.imagePath from ArTipImage i where i.tipId = :tipId", String.class)
			.setParameter("tipId", tipId).getResultList()).containsExactly("tip-photo/abc");
		assertThat(STATES.states.get(tipId).steps().media()).isEqualTo(TipPublishState.StepStatus.SUCCEEDED);
	}

	@Autowired
	DataSource dataSource;

	@Autowired
	ArTipRepository arTipRepository;

	@Autowired
	ArTipImageRepository arTipImageRepository;

	/** 사진 연결이 실패하면(같은 팁에 사진 행이 이미 있어 tip_id UNIQUE 위반) 팁은 FAILED, 작업도 FAILED·재시도 가능. */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 게시에_실패하면_팁과_작업이_FAILED이고_공개되지_않는다() {
		Long tipId = arTipRepository.save(registering(AUTHOR)).getId();
		arTipImageRepository.save(ArTipImage.of(tipId, "tip-photo/old", NOW));
		try {
			publisher.publish(new TipPublishRequested(tipId, "job-1", "tip-photo/new"));

			assertThat(arTipRepository.findById(tipId).orElseThrow().getStatus()).isEqualTo(TipStatus.FAILED);
			TipPublishState state = STATES.states.get(tipId);
			assertThat(state.operationStatus()).isEqualTo(OperationStatus.FAILED);
			assertThat(state.steps().media()).isEqualTo(TipPublishState.StepStatus.FAILED);
			assertThat(state.error().code()).isEqualTo("C014");
			assertThat(state.retryable()).isTrue();
		} finally {
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			jdbc.update("DELETE FROM ar_tip_images WHERE tip_id = ?", tipId);
			jdbc.update("DELETE FROM ar_tips WHERE id = ?", tipId);
		}
	}

	@Test
	void 이미_게시된_팁은_다시_게시하지_않고_재시도_불가_실패로_남긴다() {
		ArTip tip = registering(AUTHOR);
		tip.publish(NOW.minusSeconds(60));
		Long tipId = em.persistAndGetId(tip, Long.class);

		publisher.publish(new TipPublishRequested(tipId, "job-1", null));

		assertThat(em.find(ArTip.class, tipId).getStatus()).isEqualTo(TipStatus.PUBLISHED);
		assertNotPublishable(tipId);
	}

	@Test
	void 게시_직전_삭제된_팁은_사진을_붙이지_않고_재시도_불가_실패로_남긴다() {
		ArTip tip = registering(AUTHOR);
		tip.delete(NOW.minusSeconds(1));
		Long tipId = em.persistAndGetId(tip, Long.class);

		publisher.publish(new TipPublishRequested(tipId, "job-1", "tip-photo/abc"));

		assertThat(em.find(ArTip.class, tipId).getStatus()).isEqualTo(TipStatus.DELETED);
		assertThat(arTipImageRepository.existsByImagePath("tip-photo/abc")).isFalse();
		assertNotPublishable(tipId);
	}

	private static void assertNotPublishable(Long tipId) {
		TipPublishState state = STATES.states.get(tipId);
		assertThat(state.operationStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(state.error().code()).isEqualTo("T012");
		assertThat(state.retryable()).isFalse();
	}
}
