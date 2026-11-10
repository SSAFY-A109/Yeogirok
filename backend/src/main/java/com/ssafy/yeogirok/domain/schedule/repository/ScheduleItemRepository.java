package com.ssafy.yeogirok.domain.schedule.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;

/** 일정 항목은 날짜·순서로 읽는다. */
public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

	List<ScheduleItem> findByRoomIdOrderByVisitDateAscPositionAsc(Long roomId);

	boolean existsByRoomId(Long roomId);
}
