package com.ssafy.yeogirok.domain.tip.service;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.northOf;
import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

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
import com.ssafy.yeogirok.domain.tip.dto.TipReactionRequest.TipReactionCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipReactionResponse;
import com.ssafy.yeogirok.domain.tip.entity.TipReaction;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.global.lock.UserLock;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

/** API-052 규칙. 팁은 기준 위치에 있고, 요청 위치는 북쪽으로 약 1.11m 단위로 옮긴다. */
@DataJpaTest
@Import({TipReactionService.class, PublicTipFinder.class, LocationEvidencePolicy.class, UserLock.class,
	TipReactionServiceTest.Config.class})
class TipReactionServiceTest {

	static final Long AUTHOR = 2001L;
	static final Long VIEWER = 2002L;
	static final Long OTHER = 2003L;

	@TestConfiguration
	@EnableConfigurationProperties({TipProperties.class, LocationEvidenceProperties.class})
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}
	}

	@Autowired
	TipReactionService service;

	@Autowired
	TestEntityManager em;

	@Test
	void 변경_재전송_취소는_최종_상태로_지정한다() {
		TestUsers.insertActive(em.getEntityManager(), VIEWER);
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);

		assertThat(react(VIEWER, tipId, TipReaction.LIKE)).isEqualTo(new TipReactionResponse(tipId, TipReaction.LIKE, 1, 0));
		assertThat(react(VIEWER, tipId, TipReaction.DISLIKE))
			.isEqualTo(new TipReactionResponse(tipId, TipReaction.DISLIKE, 0, 1));
		assertThat(react(VIEWER, tipId, TipReaction.DISLIKE)).as("재전송은 토글이 아니다")
			.isEqualTo(new TipReactionResponse(tipId, TipReaction.DISLIKE, 0, 1));
		assertThat(react(VIEWER, tipId, null)).isEqualTo(new TipReactionResponse(tipId, null, 0, 0));
		assertThat(react(VIEWER, tipId, null)).as("반응이 없어도 취소는 성공")
			.isEqualTo(new TipReactionResponse(tipId, null, 0, 0));
	}

	@Test
	void 집계는_팁마다_모든_사용자의_반응을_센다() {
		TestUsers.insertActive(em.getEntityManager(), VIEWER, OTHER);
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);
		Long otherTipId = em.persistAndGetId(published(AUTHOR), Long.class);

		react(OTHER, tipId, TipReaction.LIKE);
		react(OTHER, otherTipId, TipReaction.DISLIKE);

		assertThat(react(VIEWER, tipId, TipReaction.LIKE)).isEqualTo(new TipReactionResponse(tipId, TipReaction.LIKE, 2, 0));
		assertThat(react(VIEWER, tipId, TipReaction.DISLIKE))
			.isEqualTo(new TipReactionResponse(tipId, TipReaction.DISLIKE, 1, 1));
	}

	@Test
	void 공개_팁이_아니면_T001_본인_팁이면_거리와_상관없이_T009_5m_밖이면_T004() {
		TestUsers.insertActive(em.getEntityManager(), VIEWER);
		Long registeringId = em.persistAndGetId(registering(AUTHOR), Long.class);
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);

		expect(() -> react(VIEWER, registeringId, TipReaction.LIKE), ExceptionType.TIP_NOT_FOUND);
		expect(() -> service.react(AUTHOR, tipId, new TipReactionCommand(TipReaction.LIKE, northOf(30))),
			ExceptionType.TIP_SELF_REACTION);
		expect(() -> service.react(VIEWER, tipId, new TipReactionCommand(null, northOf(5))),
			ExceptionType.TIP_OUT_OF_RANGE);
	}

	@Test
	void 위치_정확도를_검사한다() {
		Long tipId = em.persistAndGetId(published(AUTHOR), Long.class);
		LocationEvidence inaccurate = new LocationEvidence(northOf(0).latitude(), northOf(0).longitude(),
			new BigDecimal("31"), NOW);

		expect(() -> service.react(VIEWER, tipId, new TipReactionCommand(TipReaction.LIKE, inaccurate)),
			ExceptionType.LOCATION_ACCURACY_INSUFFICIENT);
	}

	@Autowired
	DataSource dataSource;

	@Autowired
	ArTipRepository arTipRepository;

	/** 같은 사용자의 같은 반응이 동시에 와도 행은 하나이고 집계도 1이다(사용자 잠금으로 한 줄로 선다). */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 동시에_같은_반응을_보내도_한_번만_센다() throws Exception {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		long viewer = 2900L;
		TestUsers.insertActive(jdbc, viewer);
		Long tipId = arTipRepository.save(published(AUTHOR)).getId();
		int threads = 4;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<TipReactionResponse>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return react(viewer, tipId, TipReaction.LIKE);
				}));
			}
			start.countDown();
			for (Future<TipReactionResponse> result : results) {
				assertThat(result.get(10, TimeUnit.SECONDS).likes()).isEqualTo(1);
			}
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ar_tip_reactions WHERE tip_id = ?", Long.class, tipId))
				.isEqualTo(1);
		} finally {
			pool.shutdownNow();
			jdbc.update("DELETE FROM ar_tip_reactions WHERE tip_id = ?", tipId);
			jdbc.update("DELETE FROM ar_tips WHERE id = ?", tipId);
			TestUsers.delete(jdbc, viewer);
		}
	}

	private TipReactionResponse react(Long userId, Long tipId, TipReaction reaction) {
		return service.react(userId, tipId, new TipReactionCommand(reaction, northOf(4)));
	}

	private static void expect(Runnable action, ExceptionType type) {
		assertThatThrownBy(action::run).extracting("exceptionType").isEqualTo(type);
	}
}
