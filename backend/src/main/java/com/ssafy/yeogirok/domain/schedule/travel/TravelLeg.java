package com.ssafy.yeogirok.domain.schedule.travel;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 두 지점 사이 이동 구간. 이동 시간을 알 수 없으면 travelMinutes·distanceMeters 는 null 이고 unavailableReason 에
 * 사유를 남긴다. 근거 없는 이동 시간을 채우지 않는다(명세 정책).
 *
 * @param source            값을 받은 곳(예: KAKAO_CAR). 조회하지 못했으면 시도한 곳 또는 null
 * @param asOf              값을 받은 시각
 * @param unavailableReason 이동 시간을 모를 때의 사유. 알면 null
 */
public record TravelLeg(Integer travelMinutes, BigDecimal distanceMeters, String source, Instant asOf,
	String unavailableReason) {

	public static TravelLeg found(int travelMinutes, BigDecimal distanceMeters, String source, Instant asOf) {
		return new TravelLeg(travelMinutes, distanceMeters, source, asOf, null);
	}

	public static TravelLeg unavailable(String source, String reason, Instant asOf) {
		return new TravelLeg(null, null, source, asOf, reason);
	}

	public boolean isKnown() {
		return travelMinutes != null;
	}
}
