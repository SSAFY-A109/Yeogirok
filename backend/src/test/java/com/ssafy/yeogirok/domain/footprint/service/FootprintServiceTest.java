package com.ssafy.yeogirok.domain.footprint.service;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.CAPTURED_AT;
import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateResponse;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

@DataJpaTest
@Import({FootprintService.class, FootprintFinder.class})
class FootprintServiceTest {

	static final Long OWNER = 1L;
	static final Long OTHER = 2L;

	@Autowired
	FootprintService footprintService;

	@Autowired
	TestEntityManager em;

	@Test
	void API_043_본인_발자취_상세를_돌려준다() {
		Long id = em.persistAndGetId(footprintOf(OWNER).build(), Long.class);
		em.flush();
		em.clear();

		FootprintDetailResponse response = footprintService.getFootprint(OWNER, id);

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.latitude()).isEqualByComparingTo(new BigDecimal("37.5012345"));
		assertThat(response.longitude()).isEqualByComparingTo(new BigDecimal("127.0398765"));
		assertThat(response.locationName()).isEqualTo("역삼동");
		assertThat(response.address()).isEqualTo("서울특별시 강남구 역삼동 123");
		assertThat(response.body()).isEqualTo("좋았다");
		assertThat(response.categoryCodes()).containsExactly("CAFE", "FOOD");
		assertThat(response.visitedAt()).isEqualTo(CAPTURED_AT);
		assertThat(response.createdAt()).isNotNull();
		assertThat(response.version()).isEqualTo(1);
	}

	@Test
	void 위치명_주소_본문이_없으면_null() {
		Long id = em.persistAndGetId(footprintOf(OWNER).locationName(null).address(null).body(null).build(), Long.class);
		em.flush();
		em.clear();

		FootprintDetailResponse response = footprintService.getFootprint(OWNER, id);

		assertThat(response.locationName()).isNull();
		assertThat(response.address()).isNull();
		assertThat(response.body()).isNull();
	}

	@Test
	void 다른_사람_발자취는_F002_없으면_F001() {
		Long id = em.persistAndGetId(footprintOf(OWNER).build(), Long.class);

		assertThatThrownBy(() -> footprintService.getFootprint(OTHER, id))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.FOOTPRINT_NOT_OWNER);
		assertThatThrownBy(() -> footprintService.getFootprint(OWNER, id + 1000))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.FOOTPRINT_NOT_FOUND);
	}

	/* ── API-044 본문 수정 ─────────────────────────────────────── */

	@Test
	void API_044_본문을_바꾸고_version을_1_올린다() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		FootprintUpdateResponse response = footprintService.updateBody(OWNER, id, 1, "다시 와도\r\n좋겠다");

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.body()).isEqualTo("다시 와도\n좋겠다");
		assertThat(response.version()).isEqualTo(2);
		assertThat(response.updatedAt()).isNotNull();

		em.clear();
		Footprint saved = em.find(Footprint.class, id);
		assertThat(saved.getBody()).isEqualTo("다시 와도\n좋겠다");
		assertThat(saved.getVersion()).isEqualTo(2);
	}

	@Test
	void 위치_방문_시각_카테고리는_바뀌지_않는다() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		footprintService.updateBody(OWNER, id, 1, "바꾼 본문");

		em.clear();
		Footprint saved = em.find(Footprint.class, id);
		assertThat(saved.getLatitude()).isEqualByComparingTo(new BigDecimal("37.5012345"));
		assertThat(saved.getLongitude()).isEqualByComparingTo(new BigDecimal("127.0398765"));
		assertThat(saved.getLocationName()).isEqualTo("역삼동");
		assertThat(saved.getAddress()).isEqualTo("서울특별시 강남구 역삼동 123");
		assertThat(saved.getLegalAreaCode()).isEqualTo("1168010100");
		assertThat(saved.getCategoryCodes()).containsExactly("CAFE", "FOOD");
		assertThat(saved.getVisitedAt()).isEqualTo(CAPTURED_AT);
		assertThat(saved.getDeletedAt()).isNull();
	}

	@Test
	void 빈_본문이면_본문만_null이_되고_발자취는_남는다() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		FootprintUpdateResponse response = footprintService.updateBody(OWNER, id, 1, "  \n ");

		assertThat(response.body()).isNull();
		assertThat(response.version()).isEqualTo(2);
		assertThat(footprintService.getFootprint(OWNER, id).body()).isNull();
	}

	@Test
	void 같은_본문으로_수정해도_version을_1_올린다() {
		Long id = persistAndClear(footprintOf(OWNER).body("좋았다").build());

		assertThat(footprintService.updateBody(OWNER, id, 1, "좋았다").version()).isEqualTo(2);
	}

	@Test
	void 두_번_수정하면_version이_차례로_오른다() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		footprintService.updateBody(OWNER, id, 1, "첫 번째");
		em.clear();

		assertThat(footprintService.updateBody(OWNER, id, 2, "두 번째").version()).isEqualTo(3);
	}

	@Test
	void 요청_version이_현재와_다르면_C004() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		expect(() -> footprintService.updateBody(OWNER, id, 2, "본문"), ExceptionType.VERSION_MISMATCH);
		expect(() -> footprintService.updateBody(OWNER, id, 0, "본문"), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 확인한_뒤_다른_요청이_먼저_수정했으면_낙관적_잠금으로_막는다() {
		Long id = persistAndClear(footprintOf(OWNER).build());
		em.find(Footprint.class, id);  // 이 요청이 version 1 을 읽어 둔 상태
		em.getEntityManager().createNativeQuery("UPDATE footprints SET version = 2 WHERE id = :id")
			.setParameter("id", id)
			.executeUpdate();  // 그 사이 다른 요청이 먼저 수정함

		assertThatThrownBy(() -> footprintService.updateBody(OWNER, id, 1, "늦은 수정"))
			.isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void 본문_201자는_C003이고_저장하지_않는다() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		expect(() -> footprintService.updateBody(OWNER, id, 1, "가".repeat(201)), ExceptionType.NOT_ALLOWED_VALUE);

		em.clear();
		assertThat(em.find(Footprint.class, id).getBody()).isEqualTo("좋았다");
		assertThat(em.find(Footprint.class, id).getVersion()).isEqualTo(1);
	}

	@Test
	void 수정도_다른_사람_발자취는_F002_없으면_F001() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		expect(() -> footprintService.updateBody(OTHER, id, 1, "본문"), ExceptionType.FOOTPRINT_NOT_OWNER);
		expect(() -> footprintService.updateBody(OWNER, id + 1000, 1, "본문"), ExceptionType.FOOTPRINT_NOT_FOUND);
	}

	private Long persistAndClear(Footprint footprint) {
		Long id = em.persistAndGetId(footprint, Long.class);
		em.flush();
		em.clear();
		return id;
	}

	private static void expect(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run)
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}
}
