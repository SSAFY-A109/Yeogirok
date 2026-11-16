package com.ssafy.yeogirok.domain.course.repository;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;

/**
 * 지금 볼 수 있는 공개 코스와 그 원본 방({@link PublicCourseRepository#findViewable}).
 *
 * @param record 게시 중인 원본 기록(= 코스)
 * @param room   삭제되지 않은 원본 방
 */
public record ViewableCourse(
	TravelRecord record,
	TravelRoom room
) {
}
