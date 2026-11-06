package com.ssafy.yeogirok.domain.schedule.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.repository.RoomPlaceRepository;
import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.domain.schedule.repository.ScheduleItemRepository;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;

import lombok.RequiredArgsConstructor;

/** 일정 편집(API-040)과 여행방 수정(API-023)이 함께 쓰는 조회. 부르는 쪽 트랜잭션 안에서 쓴다. */
@Component
@RequiredArgsConstructor
public class ScheduleReader {

	private final ScheduleItemRepository scheduleItemRepository;
	private final RoomPlaceRepository roomPlaceRepository;

	/** 방의 확정 일정 항목(날짜·순서). */
	public List<ScheduleItem> items(Long roomId) {
		return scheduleItemRepository.findByRoomIdOrderByVisitDateAscPositionAsc(roomId);
	}

	/** 방의 후보 전체(뺀 후보 포함, 장소 정보를 같이 읽는다). 후보 ID → 후보. */
	public Map<Long, RoomPlace> places(Long roomId) {
		return roomPlaceRepository.findByRoomIdOrderByIdAsc(roomId).stream()
			.collect(Collectors.toMap(RoomPlace::getId, Function.identity()));
	}

	/** 이동 시간을 구할 후보 장소의 좌표. */
	public static GeoPoint location(RoomPlace roomPlace) {
		Place place = roomPlace.getPlace();
		return new GeoPoint(place.getLatitude(), place.getLongitude());
	}
}
