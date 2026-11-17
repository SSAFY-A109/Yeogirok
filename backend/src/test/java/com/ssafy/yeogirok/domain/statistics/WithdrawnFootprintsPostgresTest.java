package com.ssafy.yeogirok.domain.statistics;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.global.config.ClockConfig;

import tools.jackson.databind.json.JsonMapper;

/**
 * 운영 DB(PostgreSQL)에서 탈퇴자 보존분 경로 전체를 돈다. Flyway 마이그레이션으로 운영 스키마를 만들고, 운영 구현
 * {@link PostgresPreservedVisitCounts} 를 Hibernate 로 부른다(매개변수 바인딩·jsonb 변환·여러 행 한 문장). 다른 테스트는 H2 라
 * {@code JpaPreservedVisitCounts} 를 쓴다. 테스트마다 트랜잭션을 롤백한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({WithdrawnFootprints.class, PostgresPreservedVisitCounts.class, VisitStatisticsService.class,
	VisitStatisticsQuery.class, ClockConfig.class, WithdrawnFootprintsPostgresTest.Config.class})
class WithdrawnFootprintsPostgresTest {

	private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
		.withEnv("POSTGRES_PASSWORD", "test")
		.withExposedPorts(5432);

	static final Long ME = 1L;
	static final Long OTHER = 2L;
	static final String SEOUL_BOX = "126.9,37.4,127.1,37.6";

	@DynamicPropertySource
	static void postgres(DynamicPropertyRegistry registry) {
		POSTGRES.start();
		registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/postgres".formatted(POSTGRES.getHost(),
			POSTGRES.getMappedPort(5432)));
		registry.add("spring.datasource.username", () -> "postgres");
		registry.add("spring.datasource.password", () -> "test");
		// 테스트 프로필은 H2 라 Flyway 를 끄고 schema.sql 을 돌린다. 여기서는 운영처럼 Flyway 만 쓴다.
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.sql.init.mode", () -> "never");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("yeogirok.visit-statistics.aggregation-version", () -> "test-v1");
		registry.add("yeogirok.visit-statistics.base-cell-size-degrees", () -> "0.01");
		registry.add("yeogirok.visit-statistics.zoom-multipliers", () -> "10:1,0:4");
		registry.add("yeogirok.visit-statistics.province-below-zoom", () -> "5");
	}

	@TestConfiguration
	static class Config {

		@Bean
		JsonMapper jsonMapper() {
			return JsonMapper.builder().build();
		}
	}

	@Autowired
	WithdrawnFootprints withdrawnFootprints;
	@Autowired
	VisitStatisticsService statisticsService;
	@Autowired
	TestEntityManager em;

	@Test
	void 탈퇴_전후로_방문_분포가_같다() {
		save(ME, "37.501", "127.001", List.of("FOOD", "CAFE"), "2026-11-04T03:00:00Z");
		save(ME, "37.509", "127.009", List.of("CAFE", "FOOD"), "2026-11-04T05:00:00Z");
		save(ME, "37.501", "127.001", List.of("CAFE"), "2026-11-04T15:30:00Z");
		save(OTHER, "37.503", "127.003", List.of("CAFE"), "2026-11-04T03:00:00Z");
		// 법정동 코드가 없는 발자취(역지오코딩 실패). 전국 보기에서 가까운 도로 넣는 SQL 갈래를 PostgreSQL 에서 돈다.
		em.persist(footprintOf(OTHER).latitude(new BigDecimal("37.504")).longitude(new BigDecimal("127.004"))
			.legalAreaCode(null).categoryCodes(List.of("CAFE")).visitedAt(Instant.parse("2026-11-04T03:00:00Z")).build());
		em.flush();
		List<VisitStatisticsResponse.Cell> before = cells(10);
		List<VisitStatisticsResponse.Cell> beforeZoomedOut = cells(7);
		List<VisitStatisticsResponse.Cell> beforeProvinces = cells(4);

		withdraw(ME);

		assertThat(cells(10)).isEqualTo(before);
		assertThat(cells(7)).isEqualTo(beforeZoomedOut);
		assertThat(beforeProvinces).extracting(VisitStatisticsResponse.Cell::cellId, VisitStatisticsResponse.Cell::count)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("province:41", 5L));
		assertThat(cells(4)).isEqualTo(beforeProvinces);
		assertThat(count("SELECT count(*) FROM footprints WHERE user_id = 1")).isZero();
		// 같은 칸·날짜·집합(정렬하면 같다)은 한 행이다.
		assertThat(count("SELECT visit_count FROM footprint_statistics WHERE cell_id = '3750:12700'"
			+ " AND category_codes = '[\"CAFE\", \"FOOD\"]'::jsonb")).isEqualTo(2);
	}

	@Test
	void 다른_회원의_보존분과_키가_같으면_더한다() {
		save(ME, "37.501", "127.001", List.of("CAFE"), "2026-11-04T03:00:00Z");
		save(OTHER, "37.502", "127.002", List.of("CAFE"), "2026-11-04T04:00:00Z");

		withdraw(OTHER);
		withdraw(ME);

		assertThat(count("SELECT sum(visit_count) FROM footprint_statistics")).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM footprint_statistics")).isEqualTo(1);
	}

	@Test
	void 키가_많으면_여러_문장으로_나눠_모두_넣는다() {
		int keys = 1023;  // 512·256·…·2·1 행 문장 열 개. 모든 단계를 한 번씩 거친다.
		for (int i = 0; i < keys; i++) {
			// 위도 칸마다 하나씩(기본 셀 0.01도).
			save(ME, new BigDecimal("30.005").add(new BigDecimal("0.01").multiply(BigDecimal.valueOf(i))).toPlainString(),
				"127.001", List.of("CAFE"), "2026-11-04T03:00:00Z");
		}

		withdraw(ME);

		assertThat(count("SELECT count(*) FROM footprint_statistics")).isEqualTo(keys);
		assertThat(count("SELECT sum(visit_count) FROM footprint_statistics")).isEqualTo(keys);
	}

	private void withdraw(Long userId) {
		withdrawnFootprints.preserveAndDelete(userId);
		em.flush();
		em.clear();
	}

	/** 칸 위치는 탈퇴 뒤 달라질 수 있어 빼고 비교한다({@link WithdrawnFootprintsTest#cellsOf}). */
	private List<VisitStatisticsResponse.Cell> cells(double zoom) {
		return WithdrawnFootprintsTest.cellsOf(statisticsService.getStatistics(SEOUL_BOX, zoom, null, null, null, null));
	}

	private void save(Long userId, String latitude, String longitude, List<String> categories, String visitedAt) {
		Footprint footprint = footprintOf(userId).latitude(new BigDecimal(latitude))
			.longitude(new BigDecimal(longitude)).categoryCodes(categories).visitedAt(Instant.parse(visitedAt)).build();
		em.persist(footprint);
		em.flush();
	}

	private long count(String sql) {
		return ((Number)em.getEntityManager().createNativeQuery(sql).getSingleResult()).longValue();
	}
}
