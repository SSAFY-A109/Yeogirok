package com.ssafy.yeogirok.domain.course.repository;

import java.time.LocalDate;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/** 검색 조건에 맞는 공개 코스 한 건(API-014). 기간·정렬·페이지는 서비스가 정한다. */
public record CourseCandidate(Long id, String title, Long roomId, LocalDate roomStart, LocalDate roomEnd,
	TransportMode transportMode, LocalDate firstVisit, LocalDate lastVisit, Long importCount) {
}
