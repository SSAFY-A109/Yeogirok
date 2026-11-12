package com.ssafy.yeogirok.domain.place.tour;

import static com.ssafy.yeogirok.domain.category.TravelCategory.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.category.TravelCategory;

/** 11/2 확정 매핑표: 소분류 예외 → 중분류 기본값 → 카테고리 없음. */
class TourCategoryMapperTest {

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
		// 중분류 기본값
		"NA01, NA010100, NATURE", "VE03, VE030100, NATURE", "HS01, HS010100, CULTURE", "VE06, VE060100, CULTURE",
		"EX01, EX010100, CULTURE", "EX04, EX040100, CULTURE", "VE01, VE010100, SIGHT", "VE05, VE050100, SIGHT",
		"FD01, FD010100, FOOD", "FD04, FD040100, FOOD", "FD05, FD050100, CAFE", "EX02, EX020100, ACTIVITY",
		"LS02, LS020100, ACTIVITY", "VE02, VE020100, ACTIVITY", "VE10, VE100100, ACTIVITY", "SH06, SH060100, SHOPPING",
		"EV03, EV030100, EVENT",
		// 소분류 예외
		"EX05, EX050100, NATURE", "EX05, EX050200, ACTIVITY", "EX05, EX050600, ACTIVITY", "EX05, EX050800, null",
		"EX06, EX060100, CULTURE", "EX06, EX060200, ACTIVITY", "FD03, FD030100, CAFE", "FD03, FD030200, FOOD",
		"VE07, VE070400, null", "VE07, VE070100, CULTURE",
		// 카테고리 없음
		"AC01, AC010100, null", "C0112, C01120001, null", "VE08, VE080100, null", "VE09, VE090100, null",
		"VE11, VE110100, null", "VE12, VE120100, null", "null, null, null", "XX99, null, null"
	})
	void 소분류_예외_중분류_기본값_카테고리_없음_순서로_정한다(String middle, String small, TravelCategory expected) {
		assertThat(TourCategoryMapper.categoryOf(middle, small)).isEqualTo(expected);
	}

	@Test
	void 소분류가_없으면_중분류_기본값() {
		assertThat(TourCategoryMapper.categoryOf("EX05", null)).isEqualTo(NATURE);
	}

	@Test
	void 카테고리가_될_수_있는_중분류_수는_매핑표와_같다() {
		assertThat(TourCategoryMapper.middleCodesOf(NATURE)).hasSize(7);
		assertThat(TourCategoryMapper.middleCodesOf(CULTURE)).hasSize(9).contains("EX06");
		assertThat(TourCategoryMapper.middleCodesOf(SIGHT)).containsExactlyInAnyOrder("VE01", "VE04", "VE05");
		assertThat(TourCategoryMapper.middleCodesOf(FOOD)).hasSize(4);
		assertThat(TourCategoryMapper.middleCodesOf(CAFE)).containsExactlyInAnyOrder("FD05", "FD03");
		assertThat(TourCategoryMapper.middleCodesOf(ACTIVITY)).hasSize(11).contains("EX05");
		assertThat(TourCategoryMapper.middleCodesOf(SHOPPING)).hasSize(7);
		assertThat(TourCategoryMapper.middleCodesOf(EVENT)).hasSize(3);
	}
}
