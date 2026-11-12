package com.ssafy.yeogirok.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.course.dto.ImportCountBasis;

/** 198 메인 집계 기간: 조회 시각(초 단위)까지 30일. */
class ImportWindowTest {

	@Test
	void 조회_시각을_초_단위로_자르고_30일_전부터() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-11T03:00:00.987Z"), ZoneOffset.UTC);

		ImportWindow window = ImportWindow.now(clock);

		assertThat(window.end()).isEqualTo(Instant.parse("2026-11-11T03:00:00Z"));
		assertThat(window.start()).isEqualTo(Instant.parse("2026-10-12T03:00:00Z"));
		assertThat(window.basis()).isEqualTo(new ImportCountBasis("IMPORT_COUNT_30D", window.start(), window.end()));
	}
}
