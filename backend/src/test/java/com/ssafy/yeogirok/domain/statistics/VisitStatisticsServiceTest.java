package com.ssafy.yeogirok.domain.statistics;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.domain.statistics.entity.FootprintStatistic;
import com.ssafy.yeogirok.domain.statistics.entity.FootprintStatisticId;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * API-046. 테스트 설정: 기본 셀 0.01도, zoom 10 이상 ×1(0.01도), 10 미만 ×4(0.04도). 최대 셀 수는 공통 설정 2500.
 * 역삼동 근처 (37.505, 127.005) 는 zoom 10 에서 칸 (3750, 12700) 이다.
 */
@DataJpaTest
@Import({VisitStatisticsService.class, VisitStatisticsQuery.class})
@TestPropertySource(properties = {
	"yeogirok.visit-statistics.aggregation-version=test-v1",
	"yeogirok.visit-statistics.base-cell-size-degrees=0.01",
	"yeogirok.visit-statistics.zoom-multipliers=10:1,0:4",
	"yeogirok.visit-statistics.min-count=1"
})
class VisitStatisticsServiceTest {

	static final String SEOUL_BOX = "126.9,37.4,127.1,37.6";

	@Autowired
	VisitStatisticsService service;

	@Autowired
	TestEntityManager em;

	@Test
	void 셀마다_삭제하지_않은_발자취_횟수를_세고_셀_중심을_준다() {
		save(at("37.501", "127.001"), List.of("CAFE"));
		save(at("37.509", "127.009"), List.of("CAFE"));
		save(at("37.515", "127.005"), List.of("CAFE"));  // 북쪽 옆 칸 (3751, 12700)
		softDelete(save(at("37.502", "127.002"), List.of("CAFE")));  // 삭제한 발자취는 세지 않는다

		VisitStatisticsResponse response = get(SEOUL_BOX, 10, null, null, null);

		assertThat(response.cells()).hasSize(2);
		VisitStatisticsResponse.Cell north = response.cells().get(0);
		VisitStatisticsResponse.Cell south = response.cells().get(1);
		assertThat(north.cellId()).isEqualTo("1:3751:12700");
		assertThat(north.count()).isEqualTo(1);
		assertThat(south.cellId()).isEqualTo("1:3750:12700");
		assertThat(south.count()).isEqualTo(2);
		assertThat(south.center().latitude()).isEqualByComparingTo("37.505");
		assertThat(south.center().longitude()).isEqualByComparingTo("127.005");
	}

	@Test
	void count는_발자취마다_1_categoryCounts는_고른_코드마다_1이고_0인_코드는_없다() {
		save(at("37.501", "127.001"), List.of("CAFE", "FOOD"));
		save(at("37.502", "127.002"), List.of("CAFE"));
		save(at("37.503", "127.003"), List.of("NATURE", "EVENT", "FOOD"));

		VisitStatisticsResponse.Cell cell = get(SEOUL_BOX, 10, null, null, null).cells().getFirst();

		assertThat(cell.count()).isEqualTo(3);
		assertThat(cell.categoryCounts()).containsExactly(  // 공통 카테고리 순서
			Map.entry("NATURE", 1L), Map.entry("FOOD", 2L), Map.entry("CAFE", 2L), Map.entry("EVENT", 1L));
	}

	@Test
	void zoom이_낮으면_작은_셀을_정확히_묶은_큰_셀로_센다() {
		// 0.04 칸 937 = 0.01 칸 3748~3751(위도 37.48~37.52), 938 = 3752~3755
		save(at("37.501", "127.001"), List.of("CAFE"));   // 0.01 칸 (3750, 12700) → 0.04 칸 (937, 3175)
		save(at("37.519", "127.035"), List.of("FOOD"));   // 0.01 칸 (3751, 12703) → 0.04 칸 (937, 3175) 같은 칸
		save(at("37.521", "127.001"), List.of("FOOD"));   // 0.01 칸 (3752, 12700) → 0.04 칸 (938, 3175)

		VisitStatisticsResponse response = get(SEOUL_BOX, 7, null, null, null);

		assertThat(response.aggregation().precision()).isEqualTo("0.04deg");
		assertThat(response.cells()).extracting(VisitStatisticsResponse.Cell::cellId, VisitStatisticsResponse.Cell::count)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("4:938:3175", 1L),
				org.assertj.core.groups.Tuple.tuple("4:937:3175", 2L));
	}

	@Test
	void bbox에_걸친_셀은_bbox_밖의_발자취까지_셀_전체를_센다() {
		save(at("37.501", "127.001"), List.of("CAFE"));  // bbox 밖이지만 bbox 가 걸친 칸 안
		save(at("37.505", "127.005"), List.of("CAFE"));  // bbox 안
		save(at("37.515", "127.005"), List.of("CAFE"));  // bbox 가 걸치지 않은 칸

		VisitStatisticsResponse response = get("127.004,37.504,127.006,37.506", 10, null, null, null);

		assertThat(response.cells()).singleElement()
			.satisfies(cell -> {
				assertThat(cell.cellId()).isEqualTo("1:3750:12700");
				assertThat(cell.count()).isEqualTo(2);
			});
	}

	@Test
	void from_to는_방문_시각의_한국_날짜로_거른다() {
		save(at("37.501", "127.001"), List.of("CAFE"), "2026-11-02T14:59:59Z");  // 11/2 KST
		save(at("37.502", "127.002"), List.of("CAFE"), "2026-11-02T15:00:00Z");  // 11/3 00:00 KST
		save(at("37.503", "127.003"), List.of("CAFE"), "2026-11-03T14:59:59Z");  // 11/3 23:59:59 KST

		VisitStatisticsResponse response = get(SEOUL_BOX, 10, null, LocalDate.parse("2026-11-03"),
			LocalDate.parse("2026-11-03"));

		assertThat(response.cells().getFirst().count()).isEqualTo(2);
		assertThat(response.period().from()).isEqualTo(LocalDate.parse("2026-11-03"));
		assertThat(response.period().to()).isEqualTo(LocalDate.parse("2026-11-03"));
	}

	@Test
	void category를_주면_그_코드를_고른_발자취만_세고_함께_고른_코드도_센다() {
		save(at("37.501", "127.001"), List.of("CAFE", "FOOD"));
		save(at("37.502", "127.002"), List.of("CAFE"));
		save(at("37.503", "127.003"), List.of("NATURE"));

		VisitStatisticsResponse.Cell cell = get(SEOUL_BOX, 10, "CAFE", null, null).cells().getFirst();

		assertThat(cell.count()).isEqualTo(2);
		assertThat(cell.categoryCounts()).containsExactly(Map.entry("FOOD", 1L), Map.entry("CAFE", 2L));
	}

	@Test
	void 탈퇴자_보존분을_같은_셀에_더한다() {
		save(at("37.501", "127.001"), List.of("CAFE"));
		preserve("test-v1", "3750:12700", "2026-11-01", "[\"CAFE\", \"FOOD\"]", "37.505", "127.005", 5);
		preserve("test-v1", "3770:12700", "2026-11-01", "[\"NATURE\"]", "37.705", "127.005", 2);  // bbox 밖

		VisitStatisticsResponse.Cell cell = get(SEOUL_BOX, 10, null, null, null).cells().getFirst();

		assertThat(cell.cellId()).isEqualTo("1:3750:12700");
		assertThat(cell.count()).isEqualTo(6);
		assertThat(cell.categoryCounts()).containsExactly(Map.entry("FOOD", 5L), Map.entry("CAFE", 6L));
	}

	@Test
	void 보존분도_큰_셀로_묶고_날짜_카테고리로_거르며_다른_집계_버전은_더하지_않는다() {
		preserve("test-v1", "3750:12700", "2026-11-01", "[\"CAFE\"]", "37.505", "127.005", 3);
		preserve("test-v1", "3751:12703", "2026-11-01", "[\"CAFE\"]", "37.515", "127.035", 4);  // 같은 0.04 칸
		preserve("test-v1", "3750:12700", "2026-10-01", "[\"CAFE\"]", "37.505", "127.005", 100);  // 기간 밖
		preserve("test-v1", "3750:12700", "2026-11-01", "[\"FOOD\"]", "37.505", "127.005", 100);  // 카테고리 밖
		preserve("old-v0", "3750:12700", "2026-11-01", "[\"CAFE\"]", "37.505", "127.005", 100);   // 다른 버전

		VisitStatisticsResponse response = get(SEOUL_BOX, 7, "CAFE", LocalDate.parse("2026-11-01"), null);

		assertThat(response.cells()).singleElement()
			.satisfies(cell -> {
				assertThat(cell.cellId()).isEqualTo("4:937:3175");
				assertThat(cell.count()).isEqualTo(7);
			});
	}

	@Test
	void 응답에_집계_기준과_기간을_담는다() {
		VisitStatisticsResponse response = get(SEOUL_BOX, 12.5, null, null, null);

		assertThat(response).extracting("metric", "period.from", "period.to", "aggregation.unit", "aggregation.version",
				"aggregation.precision", "aggregation.minCount")
			.containsExactly("VISIT_COUNT", null, null, "LAT_LNG_GRID", "test-v1", "0.01deg", 1);
		assertThat(response.cells()).isEmpty();
		assertThat(response.updatedAt()).isNotNull();
	}

	@Test
	void 요청값_오류() {
		assertThat(service.getStatistics(SEOUL_BOX, 10, null, null, null, "VISIT_COUNT").metric())
			.isEqualTo("VISIT_COUNT");  // metric 은 생략(null)하거나 VISIT_COUNT 만 받는다
		expect(() -> service.getStatistics(SEOUL_BOX, 10, null, null, null, "UNIQUE_VISITORS"),
			ExceptionType.UNSUPPORTED_METRIC);
		expect(() -> get("1,2,3", 10, null, null, null), ExceptionType.INVALID_BBOX);
		expect(() -> get(SEOUL_BOX, -1, null, null, null), ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> get(SEOUL_BOX, Double.NaN, null, null, null), ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> get(SEOUL_BOX, 10, "LODGING", null, null), ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> get(SEOUL_BOX, 10, null, LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-04")),
			ExceptionType.INVALID_DATE_RANGE);
	}

	@Test
	void 한국_밖은_세지_않고_한국_밖만_보는_bbox는_빈_결과다() {
		save(at("38.505", "127.005"), List.of("CAFE"));
		save(at("39.505", "127.005"), List.of("CAFE"));  // 위도 39 밖

		assertThat(get("127.0,38.0,127.1,40.0", 0, null, null, null).cells())
			.extracting(VisitStatisticsResponse.Cell::count).containsExactly(1L);
		assertThat(get("140.0,40.0,140.1,40.1", 10, null, null, null).cells()).isEmpty();
	}

	@Test
	void 셀_경계로_넓힌_영역의_셀_수가_2500을_넘으면_C013() {
		assertThat(get("127.0,37.0,127.49,37.49", 10, null, null, null).cells()).isEmpty();  // 50 × 50 = 2500
		expect(() -> get("127.0,37.0,127.5,37.5", 10, null, null, null), ExceptionType.INVALID_BBOX);  // 51 × 51
		expect(() -> get("124.0,33.0,132.0,39.0", 0, null, null, null), ExceptionType.INVALID_BBOX);  // 한국 전체 × 0.04도
	}

	@Nested
	@TestPropertySource(properties = "yeogirok.visit-statistics.min-count=2")
	class 최소_집계_수 {

		@Test
		void 최소_집계_수보다_적은_셀은_빼고_보존분까지_더한_값으로_판단한다() {
			save(at("37.501", "127.001"), List.of("CAFE"));                // 1개 → 빠짐
			save(at("37.515", "127.005"), List.of("CAFE"));                // 1개 + 보존 1 → 2 → 보임
			preserve("test-v1", "3751:12700", "2026-11-01", "[\"CAFE\"]", "37.515", "127.005", 1);

			VisitStatisticsResponse response = get(SEOUL_BOX, 10, null, null, null);

			assertThat(response.aggregation().minCount()).isEqualTo(2);
			assertThat(response.cells()).extracting(VisitStatisticsResponse.Cell::cellId).containsExactly("1:3751:12700");
		}
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	private VisitStatisticsResponse get(String bbox, double zoom, String category, LocalDate from, LocalDate to) {
		return service.getStatistics(bbox, zoom, category, from, to, null);
	}

	private Long save(BigDecimal[] point, List<String> categories) {
		return save(point, categories, "2026-11-04T03:00:00Z");
	}

	private Long save(BigDecimal[] point, List<String> categories, String visitedAt) {
		Footprint footprint = footprintOf(1L).latitude(point[0]).longitude(point[1]).categoryCodes(categories)
			.visitedAt(Instant.parse(visitedAt)).build();
		Long id = em.persistAndGetId(footprint, Long.class);
		em.flush();
		return id;
	}

	private void softDelete(Long id) {
		em.getEntityManager().createNativeQuery("UPDATE footprints SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
			.setParameter("id", id)
			.executeUpdate();
	}

	private void preserve(String version, String cellId, String statDate, String codes, String lat, String lng,
		long count) {
		em.persist(new FootprintStatistic(new FootprintStatisticId(version, cellId, LocalDate.parse(statDate), codes),
			new BigDecimal(lat), new BigDecimal(lng), count));
		em.flush();
	}

	private static BigDecimal[] at(String latitude, String longitude) {
		return new BigDecimal[] {new BigDecimal(latitude), new BigDecimal(longitude)};
	}

	private static void expect(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run)
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}
}
