package com.ssafy.yeogirok.domain.footprint.service;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

@DataJpaTest
@Import(FootprintFinder.class)
class FootprintFinderTest {

	static final Long OWNER = 1L;
	static final Long OTHER = 2L;

	@Autowired
	FootprintFinder footprintFinder;

	@Autowired
	TestEntityManager em;

	@Test
	void 본인_발자취는_돌려준다() {
		Long id = em.persistAndGetId(footprintOf(OWNER).build(), Long.class);

		assertThat(footprintFinder.getOwnFootprint(id, OWNER).getId()).isEqualTo(id);
	}

	@Test
	void 다른_사람_발자취는_F002() {
		Long id = em.persistAndGetId(footprintOf(OWNER).build(), Long.class);

		expect(() -> footprintFinder.getOwnFootprint(id, OTHER), ExceptionType.FOOTPRINT_NOT_OWNER);
	}

	@Test
	void 없는_발자취는_F001() {
		expect(() -> footprintFinder.getOwnFootprint(999L, OWNER), ExceptionType.FOOTPRINT_NOT_FOUND);
	}

	@Test
	void 삭제한_발자취는_본인이어도_다른_사람이어도_F001() {
		Long id = em.persistAndGetId(footprintOf(OWNER).build(), Long.class);
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE footprints SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
			.setParameter("id", id)
			.executeUpdate();
		em.clear();

		expect(() -> footprintFinder.getOwnFootprint(id, OWNER), ExceptionType.FOOTPRINT_NOT_FOUND);
		expect(() -> footprintFinder.getOwnFootprint(id, OTHER), ExceptionType.FOOTPRINT_NOT_FOUND);
	}

	private static void expect(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run)
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}
}
