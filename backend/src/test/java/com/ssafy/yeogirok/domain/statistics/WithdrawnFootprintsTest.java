package com.ssafy.yeogirok.domain.statistics;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.domain.statistics.entity.FootprintStatistic;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;
import com.ssafy.yeogirok.global.config.ClockConfig;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.support.WithdrawalJpaTestConfig;

/**
 * 탈퇴 정리 중 발자취. 설정은 withdrawal-test.properties(기본 셀 0.01도, zoom 10 이상 ×1, 10 미만 ×4, 5 미만 도 9곳).
 * 탈퇴 전후로 방문 분포(API-046) 응답이 같아야 한다.
 */
@DataJpaTest
@Import({WithdrawnFootprints.class, VisitStatisticsService.class, VisitStatisticsQuery.class, ClockConfig.class,
	WithdrawalJpaTestConfig.class})
@TestPropertySource(locations = "classpath:withdrawal-test.properties")
class WithdrawnFootprintsTest {

	static final Long ME = 1L;
	static final Long OTHER = 2L;
	static final String SEOUL_BOX = "126.9,37.4,127.1,37.6";

	@Autowired
	WithdrawnFootprints withdrawnFootprints;
	@Autowired
	VisitStatisticsService statisticsService;
	@Autowired
	TestEntityManager em;

	@Test
	void 탈퇴_전후로_방문_분포가_같고_발자취_원본은_모두_지워진다() {
		save(ME, "37.501", "127.001", List.of("FOOD", "CAFE"), "2026-11-04T03:00:00Z");
		save(ME, "37.509", "127.009", List.of("CAFE", "FOOD"), "2026-11-04T05:00:00Z");  // 같은 칸·날짜·집합
		save(ME, "37.515", "127.005", List.of("NATURE"), "2026-11-04T03:00:00Z");        // 북쪽 옆 칸
		save(ME, "37.501", "127.001", List.of("CAFE"), "2026-11-04T15:30:00Z");          // 서울 날짜로 11-05
		softDelete(save(ME, "37.502", "127.002", List.of("CAFE"), "2026-11-04T03:00:00Z"));
		Long othersFootprint = save(OTHER, "37.503", "127.003", List.of("CAFE"), "2026-11-04T03:00:00Z");
		VisitStatisticsResponse before = statisticsService.getStatistics(SEOUL_BOX, 10, null, null, null, null);
		VisitStatisticsResponse beforeZoomedOut = statisticsService.getStatistics(SEOUL_BOX, 7, null, null, null, null);
		VisitStatisticsResponse beforeProvinces = statisticsService.getStatistics(SEOUL_BOX, 4, null, null, null, null);
		VisitStatisticsResponse beforeNov5 = statisticsService.getStatistics(SEOUL_BOX, 10, null,
			LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-05"), null);

		withdraw(ME);

		assertThat(cellsOf(statisticsService.getStatistics(SEOUL_BOX, 10, null, null, null, null)))
			.isEqualTo(cellsOf(before));
		assertThat(cellsOf(statisticsService.getStatistics(SEOUL_BOX, 7, null, null, null, null)))
			.isEqualTo(cellsOf(beforeZoomedOut));
		// 전국 보기: 발자취는 법정동 코드(서울)로, 보존분은 기본 셀 중심이 가까운 도로 넣어 같은 도에 남는다.
		assertThat(cellsOf(beforeProvinces)).extracting(VisitStatisticsResponse.Cell::cellId,
				VisitStatisticsResponse.Cell::count)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("province:41", 5L));
		assertThat(cellsOf(statisticsService.getStatistics(SEOUL_BOX, 4, null, null, null, null)))
			.isEqualTo(cellsOf(beforeProvinces));
		assertThat(cellsOf(statisticsService.getStatistics(SEOUL_BOX, 10, null,
			LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-05"), null))).isEqualTo(cellsOf(beforeNov5));
		assertThat(footprintIdsOf(ME)).isEmpty();
		assertThat(footprintIdsOf(OTHER)).containsExactly(othersFootprint);
	}

	@Test
	void 카테고리는_정렬해_같은_집합끼리_합치고_기본_셀_중심을_남긴다() {
		save(ME, "37.501", "127.001", List.of("FOOD", "CAFE"), "2026-11-04T03:00:00Z");
		save(ME, "37.509", "127.009", List.of("CAFE", "FOOD"), "2026-11-04T05:00:00Z");

		withdraw(ME);

		assertThat(statistics()).singleElement().satisfies(row -> {
			assertThat(row.getId().getAggregationVersion()).isEqualTo("test-v1");
			assertThat(row.getId().getCellId()).isEqualTo("3750:12700");
			assertThat(row.getId().getStatDate()).isEqualTo(LocalDate.parse("2026-11-04"));
			assertThat(row.getId().getCategoryCodes()).isEqualTo("[\"CAFE\",\"FOOD\"]");
			assertThat(row.getCenterLatitude()).isEqualByComparingTo("37.505");
			assertThat(row.getCenterLongitude()).isEqualByComparingTo("127.005");
			assertThat(row.getVisitCount()).isEqualTo(2);
		});
	}

	@Test
	void 다른_회원의_보존분과_키가_같으면_횟수를_더한다() {
		save(ME, "37.501", "127.001", List.of("CAFE"), "2026-11-04T03:00:00Z");
		save(OTHER, "37.502", "127.002", List.of("CAFE"), "2026-11-04T04:00:00Z");

		withdraw(OTHER);
		withdraw(ME);

		assertThat(statistics()).singleElement()
			.extracting(FootprintStatistic::getVisitCount).isEqualTo(2L);
	}

	@Test
	void 발자취가_없으면_보존분도_만들지_않는다() {
		withdraw(ME);

		assertThat(statistics()).isEmpty();
	}

	private void withdraw(Long userId) {
		withdrawnFootprints.onUserWithdrawn(new UserWithdrawnEvent(userId, OAuthProvider.KAKAO, "kakao-" + userId, null));
		em.flush();
		em.clear();
	}

	private Long save(Long userId, String latitude, String longitude, List<String> categories, String visitedAt) {
		Footprint footprint = footprintOf(userId).latitude(new BigDecimal(latitude))
			.longitude(new BigDecimal(longitude)).categoryCodes(categories).visitedAt(Instant.parse(visitedAt)).build();
		Long id = em.persistAndGetId(footprint, Long.class);
		em.flush();
		return id;
	}

	private void softDelete(Long id) {
		em.getEntityManager().createNativeQuery("UPDATE footprints SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
			.setParameter("id", id)
			.executeUpdate();
	}

	private List<Long> footprintIdsOf(Long userId) {
		return em.getEntityManager()
			.createQuery("select f.id from Footprint f where f.userId = :userId", Long.class)
			.setParameter("userId", userId)
			.getResultList();
	}

	private List<FootprintStatistic> statistics() {
		return em.getEntityManager().createQuery("select s from FootprintStatistic s", FootprintStatistic.class)
			.getResultList();
	}

	/**
	 * 셀 ID·횟수·카테고리별 횟수. 칸 위치(center)는 비교하지 않는다. 발자취는 평균 위치로, 탈퇴자 보존분은 누구 것인지 몰라
	 * 기본 셀 중심으로 세므로(VisitStatisticsQuery) 탈퇴 뒤 위치는 달라질 수 있다.
	 */
	static List<VisitStatisticsResponse.Cell> cellsOf(VisitStatisticsResponse response) {
		return response.cells().stream()
			.map(cell -> new VisitStatisticsResponse.Cell(cell.cellId(), null, cell.count(), cell.categoryCounts(),
				cell.regionName()))
			.toList();
	}
}
