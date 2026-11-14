package com.ssafy.yeogirok.domain.course.seed;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/** 묶음 파일 코스 하나의 모양 검사(명세 6-3). 어긋나면 사유 코드, 맞으면 null. 그 코스만 실패로 처리한다. */
final class SeedBundleCheck {

	private SeedBundleCheck() {
	}

	static String problem(SeedBundle.Course course) {
		if (blank(course.contentId()) || blank(course.title()) || course.places().isEmpty()) {
			return "MISSING_FIELD";
		}
		for (SeedBundle.Place place : course.places()) {
			if (blank(place.contentId()) || blank(place.name()) || place.day() == null || place.order() == null
				|| place.visitDate() == null || place.mapx() == null || place.mapy() == null) {
				return "MISSING_FIELD";
			}
			if (!validTime(place.visitTime())) {
				return "INVALID_TIME";
			}
		}
		List<SeedBundle.Place> places = CourseSeeder.sorted(course.places());
		int day = 0;
		int order = 0;
		for (SeedBundle.Place place : places) {
			if (place.day() != day) {
				if (place.day() != day + 1 || place.order() != 1) {
					return "NOT_CONSECUTIVE";
				}
				day = place.day();
				order = 1;
			} else if (place.order() != ++order) {
				return "NOT_CONSECUTIVE";
			}
			if (!place.visitDate().equals(places.getFirst().visitDate().plusDays(day - 1L))) {
				return "DATE_MISMATCH";
			}
		}
		return null;
	}

	private static boolean validTime(String value) {
		if (value == null || !value.matches("\\d{2}:\\d{2}")) {
			return false;
		}
		try {
			LocalTime.parse(value);
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
