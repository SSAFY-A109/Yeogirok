package com.ssafy.yeogirok.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.category.dto.CategoryListResponse;
import com.ssafy.yeogirok.domain.category.entity.TravelCategoryEntity;

@DataJpaTest
@Import(CategoryService.class)
class CategoryServiceTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	CategoryService categoryService;

	@Test
	void TravelCategory_선언_순서대로_돌려준다() {
		em.persist(new TravelCategoryEntity("FOOD", "맛집"));
		em.persist(new TravelCategoryEntity("NATURE", "자연 힐링"));
		em.persist(new TravelCategoryEntity("CAFE", "카페"));
		em.flush();

		assertThat(categoryService.getCategories().categories()).containsExactly(
			new CategoryListResponse.Category("NATURE", "자연 힐링"),
			new CategoryListResponse.Category("FOOD", "맛집"),
			new CategoryListResponse.Category("CAFE", "카페"));
	}

	@Test
	void enum에_없는_코드의_행은_빼고_돌려준다() {
		em.persist(new TravelCategoryEntity("NATURE", "자연 힐링"));
		em.persist(new TravelCategoryEntity("UNKNOWN", "모르는 코드"));
		em.flush();

		assertThat(categoryService.getCategories().categories()).containsExactly(
			new CategoryListResponse.Category("NATURE", "자연 힐링"));
	}

	@Test
	void 행이_없으면_빈_목록() {
		assertThat(categoryService.getCategories().categories()).isEmpty();
	}
}
