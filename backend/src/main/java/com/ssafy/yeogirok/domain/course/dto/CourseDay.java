package com.ssafy.yeogirok.domain.course.dto;

import java.util.List;

/**
 * 공개 코스의 하루(노션 016 days[]). 방문이 있는 날만 있다.
 *
 * @param day 여행 첫날을 1로 센 일차(명세 course-publish-api.md 5절 2)
 */
public record CourseDay(int day, List<Item> items) {

	/** @param order 그 날짜 안의 순서(position) */
	public record Item(Long visitId, PublicPlace place, int order, List<PublicPhoto> photos) {
	}
}
