package com.ssafy.yeogirok.domain.footprint.service;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.CAPTURED_AT;
import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDeleteResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintListResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateResponse;
import com.ssafy.yeogirok.domain.footprint.dto.StatisticsStatus;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.lock.UserLock;
import com.ssafy.yeogirok.support.H2PostgresFunctions;

@DataJpaTest
@Import({FootprintService.class, FootprintFinder.class, UserLock.class, H2PostgresFunctions.class})
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

		assertThat(response).extracting("id", "locationName", "address", "body", "categoryCodes", "visitedAt", "version")
			.containsExactly(id, "역삼동", "서울특별시 강남구 역삼동 123", "좋았다", List.of("CAFE", "FOOD"), CAPTURED_AT, 1);
		assertThat(response.latitude()).isEqualByComparingTo("37.5012345");
		assertThat(response.longitude()).isEqualByComparingTo("127.0398765");
		assertThat(response.createdAt()).isNotNull();
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
		// 위치·방문 시각·카테고리는 바뀌지 않는다
		assertThat(saved).extracting("locationName", "address", "legalAreaCode", "categoryCodes", "visitedAt", "deletedAt")
			.containsExactly("역삼동", "서울특별시 강남구 역삼동 123", "1168010100", List.of("CAFE", "FOOD"), CAPTURED_AT, null);
		assertThat(saved.getLatitude()).isEqualByComparingTo("37.5012345");
		assertThat(saved.getLongitude()).isEqualByComparingTo("127.0398765");
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
	void 같은_본문이어도_수정할_때마다_version이_차례로_오른다() {
		Long id = persistAndClear(footprintOf(OWNER).body("좋았다").build());

		assertThat(footprintService.updateBody(OWNER, id, 1, "좋았다").version()).isEqualTo(2);
		em.clear();
		assertThat(footprintService.updateBody(OWNER, id, 2, "좋았다").version()).isEqualTo(3);
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

	/* ── API-045 삭제 ─────────────────────────────────────────── */

	@Test
	void API_045_소프트_삭제하고_version을_1_올린다() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		FootprintDeleteResponse response = footprintService.delete(OWNER, id, 1);

		assertThat(response.deleted()).isTrue();
		assertThat(response.statisticsStatus()).isEqualTo(StatisticsStatus.APPLIED);
		em.clear();
		Footprint saved = em.find(Footprint.class, id);  // 행은 남아 있다
		assertThat(saved.getDeletedAt()).isNotNull();
		assertThat(saved.getVersion()).isEqualTo(2);
	}

	@Test
	void 삭제하면_상세와_목록에서_빠진다() {
		Long deleted = persistAndClear(footprintOf(OWNER).build());
		Long kept = persistAndClear(footprintOf(OWNER).build());

		footprintService.delete(OWNER, deleted, 1);
		em.clear();

		expect(() -> footprintService.getFootprint(OWNER, deleted), ExceptionType.FOOTPRINT_NOT_FOUND);
		assertThat(footprintService.getMyFootprints(OWNER, null, null, null, null, null, null).items())
			.extracting(FootprintListResponse.Item::id)
			.containsExactly(kept);
	}

	@Test
	void 이미_삭제한_발자취를_또_지우면_F001() {
		Long id = persistAndClear(footprintOf(OWNER).build());
		footprintService.delete(OWNER, id, 1);
		em.clear();

		expect(() -> footprintService.delete(OWNER, id, 2), ExceptionType.FOOTPRINT_NOT_FOUND);
	}

	@Test
	void 삭제도_version이_다르면_C004이고_지우지_않는다() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		expect(() -> footprintService.delete(OWNER, id, 2), ExceptionType.VERSION_MISMATCH);

		em.clear();
		assertThat(em.find(Footprint.class, id).getDeletedAt()).isNull();
	}

	@Test
	void 삭제도_다른_사람_발자취는_F002_없으면_F001() {
		Long id = persistAndClear(footprintOf(OWNER).build());

		expect(() -> footprintService.delete(OTHER, id, 1), ExceptionType.FOOTPRINT_NOT_OWNER);
		expect(() -> footprintService.delete(OWNER, id + 1000, 1), ExceptionType.FOOTPRINT_NOT_FOUND);
		em.clear();
		assertThat(em.find(Footprint.class, id).getDeletedAt()).isNull();
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
