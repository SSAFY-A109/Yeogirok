package com.ssafy.yeogirok.domain.schedule.draft;

import java.time.Instant;
import java.time.LocalDate;

import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;

/**
 * 초안 항목 하나. 같은 후보를 여러 번 넣으면 항목마다 다른 itemId 를 가진다. 배치·미배치를 오가도 itemId 는 그대로다.
 *
 * @param itemId          초안 안에서 고유한 양의 정수. schedule_items.id 와 같다고 가정하지 않는다
 * @param visitDate       미배치면 null
 * @param order           날짜 안 방문 순서(1부터). 미배치면 null
 * @param arrivalAt       계산한 도착 시각. 모르거나 미배치면 null
 * @param departureAt     계산한 출발 시각. 모르거나 미배치면 null
 * @param legFromPrevious 직전 지점(앞 항목·첫날 출발지)에서 오는 구간. 직전 지점이 없거나 미배치면 null
 */
public record DraftItem(int itemId, Long roomPlaceId, LocalDate visitDate, Integer order, int stayMinutes,
	boolean fixed, Instant arrivalAt, Instant departureAt, TravelLeg legFromPrevious) {

	public static DraftItem unassigned(int itemId, Long roomPlaceId, int stayMinutes, boolean fixed) {
		return new DraftItem(itemId, roomPlaceId, null, null, stayMinutes, fixed, null, null, null);
	}

	public boolean placed() {
		return visitDate != null;
	}
}
