package com.ssafy.yeogirok.domain.tip.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TipSearchResponseTest {

	@Test
	void summary는_본문_앞_40자이고_넘으면_말줄임표를_붙인다() {
		assertThat(TipSearchResponse.summary("가".repeat(40))).isEqualTo("가".repeat(40));
		assertThat(TipSearchResponse.summary("가".repeat(41))).isEqualTo("가".repeat(40) + "…");
		assertThat(TipSearchResponse.summary("짧은 팁")).isEqualTo("짧은 팁");
	}

	@Test
	void 이모지는_한_글자로_세고_자르지_않는다() {
		assertThat(TipSearchResponse.summary("😀".repeat(41))).isEqualTo("😀".repeat(40) + "…");
	}
}
