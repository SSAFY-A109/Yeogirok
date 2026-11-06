package com.ssafy.yeogirok.domain.schedule.travel;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/**
 * 두 지점 사이 이동 시간. 외부 호출이라 트랜잭션·방 잠금 밖에서 부른다. 알 수 없으면(결과 없음·실패·키 없음) 예외 없이
 * {@link TravelLeg#unavailable} 을 돌려준다.
 */
public interface TravelTimeProvider {

	TravelLeg estimate(GeoPoint from, GeoPoint to, TransportMode mode);
}
