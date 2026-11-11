package com.ssafy.yeogirok.domain.course.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 공개 코스의 여행 기간(API-014·016 dayCount·nightCount, 명세 {@code specs/course-search-api.md} 5절 1). 첫날은 일차 계산과
 * 같고({@link PublicCourseReader#firstDay}), 마지막 날은 방 종료일과 마지막 방문 중 늦은 날이라 {@code days[].day} 가 dayCount 를
 * 넘지 않는다. 장소가 없는 날도 기간에 넣는다(노션 016).
 */
public record CoursePeriod(int dayCount, int nightCount) {

	private static final CoursePeriod NONE = new CoursePeriod(0, 0);

	/** @param firstVisit·lastVisit 방문이 없으면 null */
	public static CoursePeriod of(LocalDate roomStart, LocalDate roomEnd, LocalDate firstVisit, LocalDate lastVisit) {
		LocalDate first = firstVisit == null ? roomStart : PublicCourseReader.firstDay(roomStart, firstVisit);
		LocalDate last = later(roomEnd, lastVisit);
		if (first == null || last == null) {
			return NONE;
		}
		int days = (int) ChronoUnit.DAYS.between(first, last) + 1;
		return days < 1 ? NONE : new CoursePeriod(days, days - 1);
	}

	private static LocalDate later(LocalDate a, LocalDate b) {
		if (a == null || b == null) {
			return a == null ? b : a;
		}
		return a.isAfter(b) ? a : b;
	}
}
