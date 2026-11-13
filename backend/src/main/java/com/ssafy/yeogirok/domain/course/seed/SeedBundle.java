package com.ssafy.yeogirok.domain.course.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 묶음 파일(명세 {@code specs/tour-course-seed.md} 6-3). 218 스크립트 판정 결과에 222 가 소개 글·사진·지역 코드를 붙여 만든다.
 * 일차·날짜·시각은 스크립트에서만 계산하고 여기서는 그대로 쓴다(3-8).
 */
public record SeedBundle(String bundleId, List<Course> courses) {

	public SeedBundle {
		courses = courses == null ? List.of() : courses;
	}

	public record Course(String contentId, String title, String overview, Integer days, List<Place> places) {

		public Course {
			places = places == null ? List.of() : places;
		}
	}

	/** @param photo 묶음 위치 기준 상대 경로. 사진 없는 장소면 null */
	public record Place(String contentId, String name, String overview, Integer day, Integer order, LocalDate visitDate,
		String visitTime, BigDecimal mapx, BigDecimal mapy, String address, String regionCode, String regionName,
		String lclsSystm2, String lclsSystm3, String imageUrl, String cpyrhtDivCd, String photo, String photoSha256) {
	}
}
