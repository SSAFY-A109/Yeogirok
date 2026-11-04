package com.ssafy.yeogirok.domain.footprint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.footprint.FootprintProperties;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateCommand;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateResponse;
import com.ssafy.yeogirok.domain.footprint.dto.StatisticsStatus;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.ReverseGeocodeResult;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.location.LocationEvidenceProperties;
import com.ssafy.yeogirok.global.lock.UserLock;
import com.ssafy.yeogirok.support.FixedReverseGeocoder;
import com.ssafy.yeogirok.support.H2PostgresFunctions;
import com.ssafy.yeogirok.support.MutableClock;

/**
 * API-041 규칙. 서버 현재 시각은 2026-11-04 12:00(Asia/Seoul)으로 고정하고, 이날 작성 제한 구간은 04:00 부터다.
 *
 * <p>created_at 은 Hibernate 가 실제 시각으로 채우므로, 기존 발자취의 생성 시각이 중요한 테스트는 SQL 로 바꿔 둔다.
 * 위도 1도는 약 111,195m 라서 위도 0.0044876 차이는 약 499m, 0.0045056 차이는 약 501m 다.
 */
@DataJpaTest
@Import({FootprintCreateService.class, FootprintFinder.class, UserLock.class, LocationEvidencePolicy.class,
	H2PostgresFunctions.class, FootprintCreateServiceTest.Config.class})
@TestPropertySource(properties = {
	"yeogirok.location-evidence.max-age=2m",
	"yeogirok.location-evidence.max-future-skew=30s",
	"yeogirok.location-evidence.policy-version=test-location-v1",
	"yeogirok.footprint.same-day-radius-meters=500"
})
class FootprintCreateServiceTest {

	static final Long OWNER = 1L;
	static final Long OTHER = 2L;
	/** 2026-11-04 12:00 Asia/Seoul. */
	static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");
	/** 2026-11-04 04:00 Asia/Seoul. 이날 작성 제한 구간의 시작. */
	static final Instant DAY_START = Instant.parse("2026-11-03T19:00:00Z");
	static final BigDecimal LAT = new BigDecimal("37.5000000");
	static final BigDecimal LNG = new BigDecimal("127.0000000");
	static final ReverseGeocodeResult YEOKSAM =
		new ReverseGeocodeResult("1168010100", "서울특별시 강남구 테헤란로 152", "강남파이낸스센터");

	@TestConfiguration
	@EnableConfigurationProperties({LocationEvidenceProperties.class, FootprintProperties.class})
	static class Config {

		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}

		@Bean
		FixedReverseGeocoder reverseGeocoder() {
			return new FixedReverseGeocoder(YEOKSAM);
		}
	}

	@Autowired
	FootprintCreateService service;

	@Autowired
	TestEntityManager em;

	@Autowired
	MutableClock clock;

	@Autowired
	FixedReverseGeocoder geocoder;

	@Autowired
	DataSource dataSource;

	@BeforeEach
	void resetFakes() {
		clock.setInstant(NOW);
		geocoder.willReturn(YEOKSAM);
	}

	@Test
	void 저장하면_명세의_응답을_돌려주고_역지오코딩_결과와_정책_버전을_저장한다() {
		FootprintCreateResponse response = service.create(OWNER,
			command(LAT, LNG, NOW.minusSeconds(10), "또 오고\r\n싶다", List.of("CAFE", "FOOD")));

		assertThat(response.id()).isNotNull();
		assertThat(response.latitude()).isEqualByComparingTo(LAT);
		assertThat(response.longitude()).isEqualByComparingTo(LNG);
		assertThat(response.body()).isEqualTo("또 오고\n싶다");
		assertThat(response.categoryCodes()).containsExactly("CAFE", "FOOD");
		assertThat(response.visitedAt()).isEqualTo(NOW.minusSeconds(10));  // visitedAt = capturedAt (AC-01)
		assertThat(response.createdAt()).isNotNull();
		assertThat(response.statisticsStatus()).isEqualTo(StatisticsStatus.APPLIED);

		em.clear();
		Footprint saved = em.find(Footprint.class, response.id());
		assertThat(saved.getUserId()).isEqualTo(OWNER);
		assertThat(saved.getLegalAreaCode()).isEqualTo("1168010100");
		assertThat(saved.getAddress()).isEqualTo("서울특별시 강남구 테헤란로 152");
		assertThat(saved.getLocationName()).isEqualTo("강남파이낸스센터");
		assertThat(saved.getLocationCapturedAt()).isEqualTo(NOW.minusSeconds(10));
		assertThat(saved.getLocationPolicyVersion()).isEqualTo("test-location-v1");
		assertThat(saved.getVersion()).isEqualTo(1);
		assertThat(saved.getDeletedAt()).isNull();
	}

	@Test
	void 본문_없이도_저장한다() {
		FootprintCreateResponse response = service.create(OWNER, command(LAT, LNG, NOW, "   ", List.of("NATURE")));

		assertThat(response.body()).isNull();
	}

	@Test
	void 역지오코딩을_못_하면_주소_위치명_법정동_코드를_null로_두고_저장한다() {
		geocoder.willReturn(ReverseGeocodeResult.EMPTY);

		Long id = service.create(OWNER, command(LAT, LNG, NOW, null, List.of("CAFE"))).id();

		em.clear();
		Footprint saved = em.find(Footprint.class, id);
		assertThat(saved.getLegalAreaCode()).isNull();
		assertThat(saved.getAddress()).isNull();
		assertThat(saved.getLocationName()).isNull();
	}

	@ParameterizedTest(name = "위도 {0}, 경도 {1}")
	@CsvSource({"90.0000001, 127", "-90.0000001, 127", "37.5, 180.0000001", "37.5, -180.0000001"})
	void 좌표가_범위_밖이면_C011이고_역지오코딩을_부르지_않는다(BigDecimal latitude, BigDecimal longitude) {
		expect(() -> service.create(OWNER, command(latitude, longitude, NOW, null, List.of("CAFE"))),
			ExceptionType.COORDINATE_OUT_OF_RANGE);
		assertThat(geocoder.calls()).isZero();
	}

	@Test
	void 좌표_경계값은_저장한다() {
		service.create(OWNER, command(new BigDecimal("90"), new BigDecimal("180"), NOW, null, List.of("CAFE")));
		service.create(OWNER, command(new BigDecimal("-90"), new BigDecimal("-180"), NOW, null, List.of("CAFE")));
	}

	@Test
	void 카테고리_본문_오류는_C003이고_역지오코딩을_부르지_않는다() {
		expect(() -> service.create(OWNER, command(LAT, LNG, NOW, null, List.of())), ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> service.create(OWNER, command(LAT, LNG, NOW, null, List.of("CAFE", "CAFE"))),
			ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> service.create(OWNER, command(LAT, LNG, NOW, null, List.of("LODGING"))),
			ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> service.create(OWNER, command(LAT, LNG, NOW, null, List.of("CAFE", "FOOD", "NATURE", "SIGHT"))),
			ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> service.create(OWNER, command(LAT, LNG, NOW, "가".repeat(201), List.of("CAFE"))),
			ExceptionType.NOT_ALLOWED_VALUE);

		assertThat(geocoder.calls()).isZero();
		assertThat(countFootprints()).isZero();
	}

	@Test
	void 위치_측정_시각이_2분_넘게_지났거나_30초_넘게_미래면_C009() {
		expect(() -> service.create(OWNER, command(LAT, LNG, NOW.minus(Duration.ofMinutes(2)).minusMillis(1), null,
			List.of("CAFE"))), ExceptionType.LOCATION_EVIDENCE_EXPIRED);
		expect(() -> service.create(OWNER, command(LAT, LNG, NOW.plusSeconds(30).plusMillis(1), null,
			List.of("CAFE"))), ExceptionType.LOCATION_EVIDENCE_EXPIRED);
		assertThat(geocoder.calls()).isZero();
	}

	@Test
	void 위치_측정_시각_경계값은_저장한다() {
		service.create(OWNER, command(LAT, LNG, NOW.minus(Duration.ofMinutes(2)), null, List.of("CAFE")));
		service.create(OWNER, command(far(1), LNG, NOW.plusSeconds(30), null, List.of("CAFE")));
	}

	/* ── 작성 제한: 같은 날(04:00 기준) 반경 500m ──────────────── */

	@Test
	void 오늘_남긴_발자취와_500m_안이면_F003이고_저장하지_않는다() {
		existing(OWNER, LAT, LNG, DAY_START.plusSeconds(60));

		expect(() -> service.create(OWNER, command(LAT.add(new BigDecimal("0.0044876")), LNG, NOW, null,
			List.of("FOOD"))), ExceptionType.FOOTPRINT_TOO_CLOSE_TODAY);
		assertThat(countFootprints()).isEqualTo(1);
	}

	@Test
	void 같은_자리여도_카테고리가_다르면_F003() {
		existing(OWNER, LAT, LNG, DAY_START);

		expect(() -> service.create(OWNER, command(LAT, LNG, NOW, null, List.of("NATURE"))),
			ExceptionType.FOOTPRINT_TOO_CLOSE_TODAY);
	}

	@Test
	void 오늘_남긴_발자취와_500m보다_멀면_같은_동이어도_저장한다() {
		existing(OWNER, LAT, LNG, DAY_START.plusSeconds(60));

		service.create(OWNER, command(LAT.add(new BigDecimal("0.0045056")), LNG, NOW, null, List.of("FOOD")));

		assertThat(countFootprints()).isEqualTo(2);
	}

	@Test
	void 전날_구간_04시_직전에_남긴_발자취는_세지_않는다() {
		existing(OWNER, LAT, LNG, DAY_START.minusSeconds(1));  // 2026-11-04 03:59:59 Asia/Seoul

		service.create(OWNER, command(LAT, LNG, NOW, null, List.of("CAFE")));

		assertThat(countFootprints()).isEqualTo(2);
	}

	@Test
	void 자정이_지나도_04시_전이면_같은_구간이다() {
		existing(OWNER, LAT, LNG, NOW);  // 2026-11-04 12:00
		clock.setInstant(Instant.parse("2026-11-04T18:30:00Z"));  // 2026-11-05 03:30

		expect(() -> service.create(OWNER, command(LAT, LNG, clock.instant(), null, List.of("CAFE"))),
			ExceptionType.FOOTPRINT_TOO_CLOSE_TODAY);

		clock.setInstant(Instant.parse("2026-11-04T19:00:00Z"));  // 2026-11-05 04:00, 다음 구간
		service.create(OWNER, command(LAT, LNG, clock.instant(), null, List.of("CAFE")));
	}

	@Test
	void 구간은_서버_시각으로_정하고_capturedAt으로_바꾸지_않는다() {
		existing(OWNER, LAT, LNG, DAY_START.minusSeconds(60));  // 전날 구간
		clock.setInstant(DAY_START.plusSeconds(20));            // 04:00:20, 새 구간
		// 기기가 03:59:50 에 측정했어도 서버 시각이 새 구간이므로 전날 발자취와 겹치지 않는다.
		service.create(OWNER, command(LAT, LNG, DAY_START.minusSeconds(10), null, List.of("CAFE")));

		assertThat(countFootprints()).isEqualTo(2);
	}

	@Test
	void 삭제한_발자취는_세지_않아_같은_자리에_다시_쓸_수_있다() {
		Long id = existing(OWNER, LAT, LNG, DAY_START.plusSeconds(60));
		em.getEntityManager().createNativeQuery("UPDATE footprints SET deleted_at = :now WHERE id = :id")
			.setParameter("now", NOW)
			.setParameter("id", id)
			.executeUpdate();
		em.clear();

		service.create(OWNER, command(LAT, LNG, NOW, null, List.of("CAFE")));

		assertThat(countFootprints()).isEqualTo(2);
	}

	@Test
	void 다른_사람의_발자취는_세지_않는다() {
		existing(OTHER, LAT, LNG, DAY_START.plusSeconds(60));

		service.create(OWNER, command(LAT, LNG, NOW, null, List.of("CAFE")));

		assertThat(countFootprints()).isEqualTo(2);
	}

	@Test
	void dayStartOf는_Asia_Seoul_04시를_하루의_시작으로_본다() {
		assertThat(FootprintCreateService.dayStartOf(Instant.parse("2026-11-03T19:00:00Z")))   // 11-04 04:00
			.isEqualTo(Instant.parse("2026-11-03T19:00:00Z"));
		assertThat(FootprintCreateService.dayStartOf(Instant.parse("2026-11-03T18:59:59Z")))   // 11-04 03:59:59
			.isEqualTo(Instant.parse("2026-11-02T19:00:00Z"));
		assertThat(FootprintCreateService.dayStartOf(Instant.parse("2026-11-04T14:59:59Z")))   // 11-04 23:59:59
			.isEqualTo(Instant.parse("2026-11-03T19:00:00Z"));
	}

	/**
	 * 같은 사용자가 다른 멱등키로 같은 자리에 동시에 보내도 하나만 저장된다(사용자 잠금 → 검사 → 저장이 한 줄로 선다).
	 * 실제 커밋이 필요해 테스트 트랜잭션 없이 돌리고, 끝나면 만든 행을 지운다. created_at 이 실제 시각이라 시계도 실제 시각에 맞춘다.
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void PostgreSQL에서_동시에_같은_자리로_보내도_하나만_저장된다() throws Exception {
		assumeTrue(isPostgres(), "PostgreSQL 에서만 확인한다");
		Long userId = 919191L;
		Instant now = Instant.now();
		clock.setInstant(now);
		int requests = 5;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		try {
			List<Future<Object>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(executor.submit(() -> {
					start.await();
					try {
						return service.create(userId, command(LAT, LNG, now, null, List.of("CAFE")));
					} catch (BusinessException e) {
						return e.getExceptionType();
					}
				}));
			}
			start.countDown();

			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> result : results) {
				outcomes.add(result.get(10, TimeUnit.SECONDS));
			}
			assertThat(outcomes).filteredOn(FootprintCreateResponse.class::isInstance).hasSize(1);
			assertThat(outcomes).filteredOn(ExceptionType.FOOTPRINT_TOO_CLOSE_TODAY::equals).hasSize(requests - 1);
		} finally {
			executor.shutdownNow();
			new JdbcTemplate(dataSource).update("DELETE FROM footprints WHERE user_id = ?", userId);
		}
	}

	/* ── 같은 멱등키로 다시 보낸 요청 ───────────────────────────── */

	@Test
	void getCreated는_저장한_발자취를_같은_응답_모양으로_돌려준다() {
		FootprintCreateResponse created = service.create(OWNER, command(LAT, LNG, NOW, "좋았다", List.of("CAFE")));
		em.clear();

		FootprintCreateResponse again = service.getCreated(OWNER, created.id());

		assertThat(again.id()).isEqualTo(created.id());
		assertThat(again.latitude()).isEqualByComparingTo(created.latitude());
		assertThat(again.body()).isEqualTo("좋았다");
		assertThat(again.categoryCodes()).containsExactly("CAFE");
		assertThat(again.visitedAt()).isEqualTo(created.visitedAt());
		assertThat(again.statisticsStatus()).isEqualTo(StatisticsStatus.APPLIED);
	}

	@Test
	void getCreated도_삭제했으면_F001_다른_사람이면_F002() {
		Long id = existing(OWNER, LAT, LNG, NOW);

		expect(() -> service.getCreated(OTHER, id), ExceptionType.FOOTPRINT_NOT_OWNER);
		expect(() -> service.getCreated(OWNER, id + 1000), ExceptionType.FOOTPRINT_NOT_FOUND);
	}

	private static FootprintCreateCommand command(BigDecimal latitude, BigDecimal longitude, Instant capturedAt,
		String body, List<String> categoryCodes) {
		return new FootprintCreateCommand(latitude, longitude, capturedAt, body, categoryCodes);
	}

	/** 다른 테스트 발자취와 500m 넘게 떨어진 위도. */
	private static BigDecimal far(int step) {
		return LAT.add(new BigDecimal("0.01").multiply(BigDecimal.valueOf(step)));
	}

	/** 생성 시각을 정해 둔 기존 발자취. */
	private Long existing(Long userId, BigDecimal latitude, BigDecimal longitude, Instant createdAt) {
		Long id = em.persistAndGetId(Footprint.builder()
			.userId(userId)
			.latitude(latitude)
			.longitude(longitude)
			.categoryCodes(List.of("CAFE"))
			.visitedAt(createdAt)
			.locationCapturedAt(createdAt)
			.locationPolicyVersion("test-location-v1")
			.build(), Long.class);
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE footprints SET created_at = :createdAt WHERE id = :id")
			.setParameter("createdAt", createdAt)
			.setParameter("id", id)
			.executeUpdate();
		em.clear();
		return id;
	}

	private boolean isPostgres() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
		}
	}

	private long countFootprints() {
		return em.getEntityManager().createQuery("select count(f) from Footprint f", Long.class).getSingleResult();
	}

	private static void expect(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run)
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}
}
