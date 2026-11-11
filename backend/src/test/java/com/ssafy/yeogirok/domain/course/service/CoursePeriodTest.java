package com.ssafy.yeogirok.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 186 공개 코스 기간. 일차(PublicCourseReader)와 같은 첫날, 방 종료일과 마지막 방문 중 늦은 날. */
class CoursePeriodTest {

	@ParameterizedTest(name = "방 {0}~{1}, 방문 {2}~{3} → {4}일 {5}박")
	@CsvSource(nullValues = "-", value = {
		"2026-11-01, 2026-11-03, 2026-11-01, 2026-11-02, 3, 2",  // 방 기간 안: 방문 없는 마지막 날도 센다
		"2026-11-01, 2026-11-01, 2026-11-01, 2026-11-01, 1, 0",  // 당일
		"2026-11-02, 2026-11-03, 2026-10-31, 2026-11-02, 4, 3",  // 방문이 방 기간보다 앞
		"2026-11-01, 2026-11-02, 2026-11-01, 2026-11-05, 5, 4",  // 방문이 방 기간보다 뒤
		"-, -, 2026-11-01, 2026-11-02, 2, 1",                    // 방 날짜 없음: 방문 기간
		"2026-11-01, 2026-11-03, -, -, 3, 2",                    // 방문 없음: 방 기간
		"2026-11-01, -, 2026-11-02, 2026-11-02, 2, 1",           // 종료일 없음: 마지막 방문까지
		"-, -, -, -, 0, 0"                                       // 둘 다 없음
	})
	void 기간(LocalDate roomStart, LocalDate roomEnd, LocalDate firstVisit, LocalDate lastVisit, int days, int nights) {
		assertThat(CoursePeriod.of(roomStart, roomEnd, firstVisit, lastVisit)).isEqualTo(new CoursePeriod(days, nights));
	}
}
