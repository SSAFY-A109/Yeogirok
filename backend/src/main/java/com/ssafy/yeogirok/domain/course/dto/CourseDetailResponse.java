package com.ssafy.yeogirok.domain.course.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/** API-016 응답(노션 그대로). id 는 기록 ID, version 은 기록 version. 이야기·영수증·닉네임은 없다. */
public record CourseDetailResponse(
	Long id,
	String title,
	String description,
	List<PublicAuthor> authors,
	List<CourseDay> days,
	int dayCount,
	int nightCount,
	TransportMode transportMode,
	Integer version
) {
}
