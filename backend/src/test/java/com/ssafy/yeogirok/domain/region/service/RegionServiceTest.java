package com.ssafy.yeogirok.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.region.dto.RegionListResponse;
import com.ssafy.yeogirok.domain.region.entity.Region;
import com.ssafy.yeogirok.domain.region.entity.RegionLevel;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

@DataJpaTest
@Import(RegionService.class)
class RegionServiceTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	RegionService regionService;

	@BeforeEach
	void setUp() {
		em.persist(Region.province("51", "강원특별자치도", 2));
		em.persist(Region.province("11", "서울특별시", 1));
		em.persist(Region.province("50", "제주특별자치도", 3));
		em.persist(Region.district("51150", "강릉시", "51", 2));
		em.persist(Region.district("51110", "춘천시", "51", 1));
		em.persist(Region.district("11110", "종로구", "11", 1));
		em.flush();
	}

	@Test
	void parentCode가_없으면_시도를_화면_순서대로_돌려준다() {
		assertThat(regionService.getRegions(null).items()).containsExactly(
			new RegionListResponse.Item("11", "서울특별시", RegionLevel.PROVINCE, null, true),
			new RegionListResponse.Item("51", "강원특별자치도", RegionLevel.PROVINCE, null, true),
			new RegionListResponse.Item("50", "제주특별자치도", RegionLevel.PROVINCE, null, false));
	}

	@Test
	void parentCode가_있으면_그_시도의_시군구만_화면_순서대로_돌려준다() {
		assertThat(regionService.getRegions("51").items()).containsExactly(
			new RegionListResponse.Item("51110", "춘천시", RegionLevel.DISTRICT, "51", false),
			new RegionListResponse.Item("51150", "강릉시", RegionLevel.DISTRICT, "51", false));
	}

	@Test
	void 시군구가_없는_시도는_빈_목록() {
		assertThat(regionService.getRegions("50").items()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"99", "51150", "", "abc"})
	void 없는_코드나_시군구_코드를_parentCode로_주면_C003(String parentCode) {
		assertThatThrownBy(() -> regionService.getRegions(parentCode))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.NOT_ALLOWED_VALUE));
	}
}
