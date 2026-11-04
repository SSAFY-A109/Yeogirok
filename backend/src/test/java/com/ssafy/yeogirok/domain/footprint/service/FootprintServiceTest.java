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

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
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
}
