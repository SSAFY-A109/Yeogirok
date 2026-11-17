package com.ssafy.yeogirok.domain.statistics;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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

import com.ssafy.yeogirok.global.config.ClockConfig;
import org.springframework.test.context.TestPropertySource;

import com.ssafy.yeogirok.domain.course.OfficialAccount;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.domain.statistics.entity.FootprintStatistic;
import com.ssafy.yeogirok.domain.statistics.entity.FootprintStatisticId;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * API-046. 테스트 설정: 기본 셀 0.01도, zoom 10 이상 ×1(0.01도), 10 미만 ×4(0.04도). 최대 셀 수는 공통 설정 2500.
 * 역삼동 근처 (37.505, 127.005) 는 zoom 10 에서 칸 (3750, 12700) 이다.
 * zoom 9 미만은 운영 설정처럼 도 9곳으로 세므로(province-below-zoom 9) 격자는 zoom 9 이상으로 보고, 전국 보기는 {@link 전국_보기} 가 본다.
 * 전국 보기도 같은 설정을 써서 테스트 컨텍스트를 새로 만들지 않는다.
 */
@DataJpaTest
@Import({VisitStatisticsService.class, VisitStatisticsQuery.class, ClockConfig.class})
@TestPropertySource(properties = {
	"yeogirok.visit-statistics.aggregation-version=test-v1",
	"yeogirok.visit-statistics.base-cell-size-degrees=0.01",
	"yeogirok.visit-statistics.zoom-multipliers=10:1,0:4",
	"yeogirok.visit-statistics.min-count=1",
	"yeogirok.visit-statistics.province-below-zoom=9"
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

		VisitStatisticsResponse response = get(SEOUL_BOX, 9, null, null, null);

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

		VisitStatisticsResponse response = get(SEOUL_BOX, 9, "CAFE", LocalDate.parse("2026-11-01"), null);

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

		assertThat(get("127.0,38.0,127.1,40.0", 9, null, null, null).cells())
			.extracting(VisitStatisticsResponse.Cell::count).containsExactly(1L);
		assertThat(get("140.0,40.0,140.1,40.1", 10, null, null, null).cells()).isEmpty();
	}

	@Test
	void 셀_경계로_넓힌_영역의_셀_수가_2500을_넘으면_C013() {
		assertThat(get("127.0,37.0,127.49,37.49", 10, null, null, null).cells()).isEmpty();  // 50 × 50 = 2500
		expect(() -> get("127.0,37.0,127.5,37.5", 10, null, null, null), ExceptionType.INVALID_BBOX);  // 51 × 51
		expect(() -> get("124.0,33.0,132.0,39.0", 9, null, null, null), ExceptionType.INVALID_BBOX);  // 한국 전체 × 0.04도
	}

	/** 230: center 는 발자취 평균 위치. 실제 사용자 발자취가 1~2개(center-min-count 3 미만)면 셀 중심(3-1). */
	@Nested
	class 셀_위치 {

		// 공식 계정이 빈 DB 에서 회원 번호 1 을 받으므로 실제 사용자는 겹치지 않는 번호를 쓴다.
		static final long USER_1 = 9001L;
		static final long USER_2 = 9002L;
		static final long USER_3 = 9003L;
		static final long USER_4 = 9004L;

		@Test
		void 공식_계정_발자취만_있으면_좌표_평균을_소수_7자리로_준다() {
			long official = officialUser();
			save(official, at("37.501", "127.001"), List.of("CULTURE"));
			save(official, at("37.502", "127.008"), List.of("CULTURE"));
			save(official, at("37.502", "127.008"), List.of("NATURE"));  // 카테고리 집합이 달라도 같은 셀이면 함께 평균

			VisitStatisticsResponse.Center center = get(SEOUL_BOX, 10, null, null, null).cells().getFirst().center();

			assertThat(center.latitude()).isEqualByComparingTo("37.5016667");
			assertThat(center.longitude()).isEqualByComparingTo("127.0056667");
		}

		@Test
		void 실제_사용자_발자취가_3개_미만이면_공식_발자취가_섞여도_셀_중심을_준다() {
			long official = officialUser();
			save(official, at("37.501", "127.001"), List.of("CULTURE"));
			save(official, at("37.501", "127.001"), List.of("CULTURE"));
			save(USER_1, at("37.502", "127.002"), List.of("CAFE"));  // 평균(37.5015)이 셀 중심과 다르게
			save(USER_2, at("37.502", "127.002"), List.of("CAFE"));

			VisitStatisticsResponse.Cell cell = get(SEOUL_BOX, 10, null, null, null).cells().getFirst();

			assertThat(cell.count()).isEqualTo(4);
			assertThat(cell.center().latitude()).isEqualByComparingTo("37.505");
			assertThat(cell.center().longitude()).isEqualByComparingTo("127.005");
		}

		@Test
		void 실제_사용자_발자취가_3개_이상이면_삭제한_것을_빼고_모든_발자취_평균을_준다() {
			long official = officialUser();
			save(official, at("37.501", "127.001"), List.of("CULTURE"));
			save(USER_1, at("37.502", "127.002"), List.of("CAFE"));
			save(USER_2, at("37.503", "127.003"), List.of("CAFE"));
			save(USER_3, at("37.506", "127.006"), List.of("CAFE"));
			softDelete(save(USER_4, at("37.509", "127.009"), List.of("CAFE")));

			VisitStatisticsResponse.Center center = get(SEOUL_BOX, 10, null, null, null).cells().getFirst().center();

			assertThat(center.latitude()).isEqualByComparingTo("37.503");
			assertThat(center.longitude()).isEqualByComparingTo("127.003");
		}

		@Test
		void 탈퇴자_보존분은_실제_사용자로_세고_기본_셀_중심을_횟수만큼_평균에_넣는다() {
			long official = officialUser();
			save(official, at("37.501", "127.001"), List.of("CULTURE"));
			preserve("test-v1", "3750:12700", "2026-11-01", "[\"CAFE\"]", "37.505", "127.005", 3);

			VisitStatisticsResponse.Center center = get(SEOUL_BOX, 10, null, null, null).cells().getFirst().center();

			assertThat(center.latitude()).isEqualByComparingTo("37.504");  // (37.501 + 37.505 × 3) / 4
			assertThat(center.longitude()).isEqualByComparingTo("127.004");
		}

		@Test
		void category를_주면_거른_발자취만으로_평균과_실제_사용자_수를_센다() {
			save(USER_1, at("37.501", "127.001"), List.of("CAFE"));
			save(USER_2, at("37.502", "127.002"), List.of("CAFE"));
			save(USER_3, at("37.503", "127.003"), List.of("CAFE"));
			save(USER_4, at("37.509", "127.009"), List.of("FOOD"));

			VisitStatisticsResponse.Center cafe = get(SEOUL_BOX, 10, "CAFE", null, null).cells().getFirst().center();
			VisitStatisticsResponse.Center food = get(SEOUL_BOX, 10, "FOOD", null, null).cells().getFirst().center();

			assertThat(cafe.latitude()).isEqualByComparingTo("37.502");
			assertThat(food.latitude()).isEqualByComparingTo("37.505");  // 실제 사용자 1개 → 셀 중심
		}

		private long officialUser() {
			return em.persistAndGetId(User.signUp(OfficialAccount.PROVIDER, OfficialAccount.SUBJECT, null, "공식", null,
				Instant.parse("2026-11-01T00:00:00Z")), Long.class);
		}
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

	/** 전국 보기(zoom 9 미만, 운영 설정과 같다). 도 9곳으로 세고 특별시·광역시는 둘러싼 도에 합친다. */
	@Nested
	class 전국_보기 {

		static final String KOREA_BOX = "124.0,33.0,132.0,39.0";

		@Test
		void 법정동_코드_앞_2자리로_도를_나누고_광역시는_둘러싼_도에_더하며_도마다_정한_위치를_준다() {
			saveIn("1168010100", at("37.501", "127.039"), List.of("CAFE"));    // 서울 강남
			saveIn("2818510100", at("37.386", "126.639"), List.of("FOOD"));    // 인천 연수
			saveIn("4111710100", at("37.263", "127.028"), List.of("FOOD"));    // 경기 수원
			saveIn("2611010100", at("35.106", "129.032"), List.of("CAFE"));    // 부산 중구
			saveIn("5011010100", at("33.499", "126.531"), List.of("NATURE"));  // 제주시

			VisitStatisticsResponse response = get(KOREA_BOX, 8, null, null, null);

			assertThat(response.aggregation().unit()).isEqualTo("PROVINCE");
			assertThat(response.aggregation().precision()).isEqualTo("province");
			assertThat(response.cells())  // 도 순서
				.extracting(VisitStatisticsResponse.Cell::cellId, VisitStatisticsResponse.Cell::regionName,
					VisitStatisticsResponse.Cell::count)
				.containsExactly(
					tuple("province:41", "서울·인천·경기", 3L),
					tuple("province:48", "부산·울산·경남", 1L),
					tuple("province:50", "제주", 1L));
			VisitStatisticsResponse.Cell capital = response.cells().getFirst();
			assertThat(capital.categoryCounts()).containsExactly(Map.entry("FOOD", 2L), Map.entry("CAFE", 1L));
			assertThat(capital.center().latitude()).isEqualByComparingTo("37.5");  // 발자취 평균이 아니다
			assertThat(capital.center().longitude()).isEqualByComparingTo("127.2");
		}

		@Test
		void 화면_영역과_상관없이_도_전체를_세고_한국_밖만_보면_빈_결과다() {
			saveIn("4719010100", at("36.119", "128.344"), List.of("CAFE"));  // 경북 구미. 서울 화면 밖

			assertThat(get(SEOUL_BOX, 8, null, null, null).cells())
				.extracting(VisitStatisticsResponse.Cell::cellId).containsExactly("province:47");
			VisitStatisticsResponse outside = get("140.0,40.0,140.1,40.1", 8, null, null, null);
			assertThat(outside.aggregation().unit()).isEqualTo("PROVINCE");
			assertThat(outside.cells()).isEmpty();
		}

		@Test
		void 법정동_코드가_없거나_모르는_코드면_위치가_가까운_도로_넣는다() {
			saveIn(null, at("35.180", "129.075"), List.of("CAFE"));          // 부산 앞바다(역지오코딩 실패)
			saveIn(null, at("37.881", "127.730"), List.of("CAFE"));          // 춘천
			saveIn("9911010100", at("33.253", "126.560"), List.of("CAFE"));  // 모르는 코드, 서귀포

			assertThat(get(KOREA_BOX, 8, null, null, null).cells())
				.extracting(VisitStatisticsResponse.Cell::cellId, VisitStatisticsResponse.Cell::count)
				.containsExactly(tuple("province:51", 1L), tuple("province:48", 1L), tuple("province:50", 1L));
		}

		@Test
		void 바뀌기_전_법정동_코드도_지금의_도로_센다() {
			saveIn("4215010100", at("37.752", "128.876"), List.of("CAFE"));  // 강원도 강릉(42)
			saveIn("4511310100", at("35.824", "127.148"), List.of("CAFE"));  // 전라북도 전주(45)
			saveIn("2911010100", at("35.146", "126.923"), List.of("CAFE"));  // 광주광역시(29)
			saveIn("4611010100", at("34.812", "126.392"), List.of("CAFE"));  // 전라남도 목포(46)

			assertThat(get(KOREA_BOX, 8, null, null, null).cells())
				.extracting(VisitStatisticsResponse.Cell::cellId, VisitStatisticsResponse.Cell::count)
				.containsExactly(tuple("province:51", 1L), tuple("province:52", 1L), tuple("province:12", 2L));
		}

		@Test
		void 탈퇴자_보존분은_기본_셀_중심이_가까운_도에_더하고_다른_집계_버전은_더하지_않는다() {
			saveIn("2711010100", at("35.869", "128.596"), List.of("CAFE"));  // 대구
			preserve("test-v1", "3586:12859", "2026-11-01", "[\"CAFE\"]", "35.865", "128.595", 3);  // 대구 기본 셀
			preserve("old-v0", "3586:12859", "2026-11-01", "[\"CAFE\"]", "35.865", "128.595", 100);

			assertThat(get(KOREA_BOX, 8, null, null, null).cells())
				.extracting(VisitStatisticsResponse.Cell::cellId, VisitStatisticsResponse.Cell::count)
				.containsExactly(tuple("province:47", 4L));
		}

		@Test
		void category와_기간으로_거른다() {
			saveIn("1168010100", at("37.501", "127.039"), List.of("CAFE", "FOOD"));
			saveIn("1168010100", at("37.502", "127.039"), List.of("NATURE"));
			saveIn("1168010100", at("37.503", "127.039"), List.of("CAFE"), "2026-10-01T03:00:00Z");  // 기간 밖

			VisitStatisticsResponse.Cell cell = get(KOREA_BOX, 8, "CAFE", LocalDate.parse("2026-11-01"), null)
				.cells().getFirst();

			assertThat(cell.count()).isEqualTo(1);
			assertThat(cell.categoryCounts()).containsExactly(Map.entry("FOOD", 1L), Map.entry("CAFE", 1L));
		}

		@Test
		void zoom이_9_이상이면_격자로_세고_9보다_조금이라도_작으면_도로_센다() {
			saveIn("1168010100", at("37.501", "127.001"), List.of("CAFE"));

			assertThat(get(SEOUL_BOX, 9, null, null, null).aggregation().unit()).isEqualTo("LAT_LNG_GRID");
			assertThat(get(SEOUL_BOX, 8.9, null, null, null).aggregation().unit()).isEqualTo("PROVINCE");
		}

		@Test
		void 한국_전체를_봐도_셀_수_제한에_걸리지_않는다() {
			assertThat(get(KOREA_BOX, 0, null, null, null).cells()).isEmpty();
		}

		@Nested
		@TestPropertySource(properties = "yeogirok.visit-statistics.min-count=2")
		class 최소_집계_수 {

			@Test
			void 최소_집계_수보다_적은_도는_뺀다() {
				saveIn("1168010100", at("37.501", "127.039"), List.of("CAFE"));
				saveIn("4111710100", at("37.263", "127.028"), List.of("CAFE"));  // 서울과 합쳐 2 → 보임
				saveIn("5011010100", at("33.499", "126.531"), List.of("CAFE"));  // 1 → 빠짐

				assertThat(get(KOREA_BOX, 8, null, null, null).cells())
					.extracting(VisitStatisticsResponse.Cell::cellId).containsExactly("province:41");
			}
		}

		private void saveIn(String legalAreaCode, BigDecimal[] point, List<String> categories) {
			saveIn(legalAreaCode, point, categories, "2026-11-04T03:00:00Z");
		}

		private void saveIn(String legalAreaCode, BigDecimal[] point, List<String> categories, String visitedAt) {
			em.persist(footprintOf(1L).latitude(point[0]).longitude(point[1]).legalAreaCode(legalAreaCode)
				.categoryCodes(categories).visitedAt(Instant.parse(visitedAt)).build());
			em.flush();
		}
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	private VisitStatisticsResponse get(String bbox, double zoom, String category, LocalDate from, LocalDate to) {
		return service.getStatistics(bbox, zoom, category, from, to, null);
	}

	private Long save(BigDecimal[] point, List<String> categories) {
		return save(point, categories, "2026-11-04T03:00:00Z");
	}

	private Long save(long userId, BigDecimal[] point, List<String> categories) {
		return save(userId, point, categories, "2026-11-04T03:00:00Z");
	}

	private Long save(BigDecimal[] point, List<String> categories, String visitedAt) {
		return save(1L, point, categories, visitedAt);
	}

	private Long save(long userId, BigDecimal[] point, List<String> categories, String visitedAt) {
		Footprint footprint = footprintOf(userId).latitude(point[0]).longitude(point[1]).categoryCodes(categories)
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
