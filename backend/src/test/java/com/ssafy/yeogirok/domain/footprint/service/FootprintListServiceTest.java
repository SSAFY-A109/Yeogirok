package com.ssafy.yeogirok.domain.footprint.service;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintListResponse;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.config.ClockConfig;
import com.ssafy.yeogirok.global.lock.UserLock;

/** API-042 내 발자취 목록. */
@DataJpaTest
@Import({FootprintService.class, FootprintFinder.class, UserLock.class, ClockConfig.class})
class FootprintListServiceTest {

	static final Long ME = 1L;
	static final Long OTHER = 2L;

	@Autowired
	FootprintService footprintService;

	@Autowired
	TestEntityManager em;

	@Test
	void 본인의_삭제하지_않은_발자취만_visitedAt_최신순으로_준다() {
		Long old = save(footprintOf(ME).visitedAt(at("2026-11-01T03:00:00Z")));
		Long recent = save(footprintOf(ME).visitedAt(at("2026-11-03T03:00:00Z")));
		Long middle = save(footprintOf(ME).visitedAt(at("2026-11-02T03:00:00Z")));
		save(footprintOf(OTHER).visitedAt(at("2026-11-03T04:00:00Z")));
		Long deleted = save(footprintOf(ME).visitedAt(at("2026-11-03T05:00:00Z")));
		softDelete(deleted);

		FootprintListResponse response = list(null, null, null, null, null, null);

		assertThat(ids(response)).containsExactly(recent, middle, old);
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	void 응답_항목에_명세의_필드를_담는다() {
		Long id = save(footprintOf(ME).locationName(null).address(null).body(null));

		FootprintListResponse.Item item = list(null, null, null, null, null, null).items().getFirst();

		assertThat(item).extracting("id", "locationName", "address", "body", "categoryCodes", "visitedAt")
			.containsExactly(id, null, null, null, List.of("CAFE", "FOOD"), at("2026-11-04T03:00:00Z"));
		assertThat(item.latitude()).isEqualByComparingTo("37.5012345");
		assertThat(item.longitude()).isEqualByComparingTo("127.0398765");
	}

	@Test
	void 커서로_이어_받으면_빠짐없이_겹치지_않게_받고_마지막_쪽이_딱_맞으면_nextCursor는_null() {
		List<Long> expected = new ArrayList<>();
		for (int day = 4; day >= 1; day--) {
			expected.add(save(footprintOf(ME).visitedAt(at("2026-11-0%dT03:00:00Z".formatted(day)))));
		}

		FootprintListResponse first = list(null, null, null, null, null, 2);
		FootprintListResponse second = list(null, null, null, null, first.nextCursor(), 2);

		assertThat(ids(first)).containsExactlyElementsOf(expected.subList(0, 2));
		assertThat(ids(second)).containsExactlyElementsOf(expected.subList(2, 4));
		assertThat(first.nextCursor()).isNotNull();
		assertThat(second.nextCursor()).isNull();
	}

	@Test
	void 방문_시각이_같으면_id_큰_순이고_커서도_그_순서를_따른다() {
		Instant same = at("2026-11-04T03:00:00Z");
		Long a = save(footprintOf(ME).visitedAt(same));
		Long b = save(footprintOf(ME).visitedAt(same));
		Long c = save(footprintOf(ME).visitedAt(same));

		FootprintListResponse first = list(null, null, null, null, null, 2);
		FootprintListResponse second = list(null, null, null, null, first.nextCursor(), 2);

		assertThat(ids(first)).containsExactly(c, b);
		assertThat(ids(second)).containsExactly(a);
		assertThat(second.nextCursor()).isNull();  // 마지막 쪽이 덜 차도 끝
	}

	@Test
	void limit을_생략하면_20개씩_준다() {
		for (int i = 0; i < 21; i++) {
			save(footprintOf(ME).visitedAt(at("2026-11-04T03:00:00Z").plusSeconds(i)));
		}

		FootprintListResponse response = list(null, null, null, null, null, null);

		assertThat(response.items()).hasSize(20);
		assertThat(response.nextCursor()).isNotNull();
	}

	@Test
	void limit은_1부터_50까지_받는다() {
		save(footprintOf(ME));

		assertThat(list(null, null, null, null, null, 1).items()).hasSize(1);
		assertThat(list(null, null, null, null, null, 50).items()).hasSize(1);
		expect(() -> list(null, null, null, null, null, 0), ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
		expect(() -> list(null, null, null, null, null, 51), ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
	}

	@Test
	void bbox_안의_발자취만_준다() {
		Long inside = save(footprintOf(ME).latitude(new BigDecimal("37.5")).longitude(new BigDecimal("127.0")));
		Long onEdge = save(footprintOf(ME).latitude(new BigDecimal("37.6")).longitude(new BigDecimal("127.1")));
		save(footprintOf(ME).latitude(new BigDecimal("35.1")).longitude(new BigDecimal("129.0")));  // 부산

		FootprintListResponse response = list("126.9,37.4,127.1,37.6", null, null, null, null, null);

		assertThat(ids(response)).containsExactlyInAnyOrder(inside, onEdge);
	}

	@Test
	void from_to는_방문_시각의_한국_날짜로_거르고_양끝을_포함한다() {
		save(footprintOf(ME).visitedAt(at("2026-11-02T14:59:59Z")));             // 11/2 23:59:59 KST
		Long startOfFrom = save(footprintOf(ME).visitedAt(at("2026-11-02T15:00:00Z")));  // 11/3 00:00 KST
		Long endOfTo = save(footprintOf(ME).visitedAt(at("2026-11-04T14:59:59Z")));      // 11/4 23:59:59 KST
		save(footprintOf(ME).visitedAt(at("2026-11-04T15:00:00Z")));             // 11/5 00:00 KST

		FootprintListResponse response = list(null, date("2026-11-03"), date("2026-11-04"), null, null, null);

		assertThat(ids(response)).containsExactly(endOfTo, startOfFrom);
	}

	@Test
	void from이나_to만_있어도_거른다() {
		Long early = save(footprintOf(ME).visitedAt(at("2026-11-01T03:00:00Z")));
		Long late = save(footprintOf(ME).visitedAt(at("2026-11-05T03:00:00Z")));

		assertThat(ids(list(null, date("2026-11-03"), null, null, null, null))).containsExactly(late);
		assertThat(ids(list(null, null, date("2026-11-03"), null, null, null))).containsExactly(early);
	}

	@Test
	void category는_그_코드를_고른_발자취만_준다() {
		Long cafeAndFood = save(footprintOf(ME).categoryCodes(List.of("CAFE", "FOOD")));
		Long cafeOnly = save(footprintOf(ME).categoryCodes(List.of("CAFE")));
		Long foodOnly = save(footprintOf(ME).categoryCodes(List.of("FOOD")));
		save(footprintOf(ME).categoryCodes(List.of("NATURE", "EVENT")));

		assertThat(ids(list(null, null, null, "CAFE", null, null))).containsExactlyInAnyOrder(cafeAndFood, cafeOnly);
		assertThat(ids(list(null, null, null, "FOOD", null, null))).containsExactlyInAnyOrder(cafeAndFood, foodOnly);
		assertThat(ids(list(null, null, null, "SHOPPING", null, null))).isEmpty();
	}

	@Test
	void 조건_값이_틀리면_명세의_오류() {
		expect(() -> list(null, date("2026-11-05"), date("2026-11-04"), null, null, null),
			ExceptionType.INVALID_DATE_RANGE);
		expect(() -> list(null, null, null, "LODGING", null, null), ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> list(null, null, null, "cafe", null, null), ExceptionType.NOT_ALLOWED_VALUE);
		expect(() -> list(null, null, null, null, "not-a-cursor", null), ExceptionType.INVALID_CURSOR);
		expect(() -> list("1,2,3", null, null, null, null, null), ExceptionType.INVALID_BBOX);
	}

	/** from 과 to 를 같은 날로 주면 그날만 받는 것도 여기서 본다. */
	@Test
	void 조건을_함께_쓰면_모두_만족하는_것만_준다() {
		Long match = save(footprintOf(ME).categoryCodes(List.of("CAFE")).visitedAt(at("2026-11-04T03:00:00Z")));
		save(footprintOf(ME).categoryCodes(List.of("FOOD")).visitedAt(at("2026-11-04T03:00:00Z")));
		save(footprintOf(ME).categoryCodes(List.of("CAFE")).visitedAt(at("2026-10-01T03:00:00Z")));
		save(footprintOf(ME).categoryCodes(List.of("CAFE")).visitedAt(at("2026-11-05T03:00:00Z")));
		save(footprintOf(ME).categoryCodes(List.of("CAFE")).visitedAt(at("2026-11-04T03:00:00Z"))
			.latitude(new BigDecimal("35.1")).longitude(new BigDecimal("129.0")));

		FootprintListResponse response = list("126.9,37.4,127.1,37.6", date("2026-11-04"), date("2026-11-04"),
			"CAFE", null, null);

		assertThat(ids(response)).containsExactly(match);
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	private FootprintListResponse list(String bbox, LocalDate from, LocalDate to, String category, String cursor,
		Integer limit) {
		return footprintService.getMyFootprints(ME, bbox, from, to, category, cursor, limit);
	}

	private Long save(Footprint.FootprintBuilder builder) {
		Long id = em.persistAndGetId(builder.build(), Long.class);
		em.flush();
		return id;
	}

	private void softDelete(Long id) {
		em.getEntityManager().createNativeQuery("UPDATE footprints SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
			.setParameter("id", id)
			.executeUpdate();
		em.clear();
	}

	private static List<Long> ids(FootprintListResponse response) {
		return response.items().stream().map(FootprintListResponse.Item::id).toList();
	}

	private static Instant at(String instant) {
		return Instant.parse(instant);
	}

	private static LocalDate date(String date) {
		return LocalDate.parse(date);
	}

	private static void expect(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run)
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}
}
