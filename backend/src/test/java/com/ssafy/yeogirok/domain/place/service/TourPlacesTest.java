package com.ssafy.yeogirok.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.repository.PlaceRepository;

/** 투어API 장소 찾기·처음 저장(명세 tour-course-seed.md 10절, AC-02·04). 029 추천 담기와 공식 코스 적재가 같이 쓴다. */
class TourPlacesTest {

	static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");

	static Place place(String id, String name) {
		return Place.tourApi(id, name, new BigDecimal("37.75"), new BigDecimal("128.87"), null, null, null, null, null,
			null, NOW);
	}

	/** 동시 저장 경쟁의 UNIQUE 위반 처리는 DB 로 매번 같은 순서를 만들기 어려워 저장소를 가짜로 둔다. */
	@Nested
	class 저장_충돌 {

		@Test
		void 저장하다_UNIQUE_위반이면_먼저_저장된_행을_다시_읽어_돌려준다() {
			PlaceRepository repository = mock(PlaceRepository.class);
			PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
			given(transactions.getTransaction(any())).willReturn(new SimpleTransactionStatus());
			Place winner = place("t-1", "먼저 저장");
			given(repository.findByProviderAndProviderPlaceId(PlaceProvider.TOUR_API, "t-1"))
				.willReturn(Optional.empty(), Optional.empty(), Optional.of(winner));
			given(repository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk_places_provider_place"));

			Place found = new TourPlaces(repository, transactions).findOrCreate("t-1", () -> place("t-1", "나중 저장"));

			assertThat(found).isSameAs(winner);
		}
	}

	@Nested
	@DataJpaTest
	@Import(TourPlaces.class)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	class 실제_DB {

		@Autowired
		TourPlaces tourPlaces;
		@Autowired
		PlaceRepository placeRepository;
		@Autowired
		PlatformTransactionManager transactionManager;
		@Autowired
		DataSource dataSource;

		@AfterEach
		void tearDown() {
			new JdbcTemplate(dataSource).update("DELETE FROM places WHERE provider_place_id LIKE 'tp-%'");
		}

		long rows(String id) {
			return new JdbcTemplate(dataSource).queryForObject(
				"SELECT COUNT(*) FROM places WHERE provider = 'TOUR_API' AND provider_place_id = ?", Long.class, id);
		}

		@Test
		void 있으면_만드는_함수를_부르지_않고_그_행을_값을_고치지_않고_돌려준다() {
			Place saved = placeRepository.saveAndFlush(place("tp-1", "처음 이름"));
			AtomicInteger made = new AtomicInteger();

			Place found = tourPlaces.findOrCreate("tp-1", () -> {
				made.incrementAndGet();
				return place("tp-1", "다른 이름");
			});

			assertThat(found.getId()).isEqualTo(saved.getId());
			assertThat(found.getName()).isEqualTo("처음 이름");
			assertThat(made).hasValue(0);
		}

		@Test
		void 없으면_만든다() {
			Place created = tourPlaces.findOrCreate("tp-2", () -> place("tp-2", "새 장소"));

			assertThat(created.getId()).isNotNull();
			assertThat(rows("tp-2")).isEqualTo(1);
		}

		@Test
		void 바깥_트랜잭션_안에서_불러도_바깥이_깨지지_않고_되돌려져도_장소는_남는다() {
			TransactionTemplate outer = new TransactionTemplate(transactionManager);

			outer.executeWithoutResult(status -> {
				tourPlaces.findOrCreate("tp-3", () -> place("tp-3", "바깥 안에서"));
				// 바깥 트랜잭션이 계속 쓸 수 있다.
				assertThat(placeRepository.findByProviderAndProviderPlaceId(PlaceProvider.TOUR_API, "tp-3")).isPresent();
				status.setRollbackOnly();
			});

			assertThat(rows("tp-3")).isEqualTo(1);
		}

		@Test
		void 동시에_처음_저장해도_한_행만_생기고_모두_같은_장소를_받는다() throws Exception {
			int threads = 4;
			CountDownLatch ready = new CountDownLatch(threads);
			ExecutorService pool = Executors.newFixedThreadPool(threads);
			try {
				List<Future<Long>> results = new ArrayList<>();
				for (int i = 0; i < threads; i++) {
					results.add(pool.submit(() -> tourPlaces.findOrCreate("tp-race", () -> {
						// 모두 '없음'을 본 뒤에 저장하러 가게 맞춘다.
						ready.countDown();
						try {
							ready.await(5, TimeUnit.SECONDS);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						return place("tp-race", "경쟁");
					}).getId()));
				}
				List<Long> ids = new ArrayList<>();
				for (Future<Long> result : results) {
					ids.add(result.get(10, TimeUnit.SECONDS));
				}

				assertThat(ids).containsOnly(ids.getFirst());
				assertThat(rows("tp-race")).isEqualTo(1);
			} finally {
				pool.shutdownNow();
			}
		}
	}
}
