package com.ssafy.yeogirok.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;

/** 실제 길찾기를 부르지 않고 정해 둔 이동 시간을 돌려준다. 받은 이동 수단을 기록한다. */
public class FakeTravelTimeProvider implements TravelTimeProvider {

	public static final String SOURCE = "FAKE";
	public static final Instant AS_OF = Instant.parse("2026-11-05T00:00:00Z");

	private Integer minutes;
	private final List<TransportMode> calls = new ArrayList<>();

	public FakeTravelTimeProvider(Integer minutes) {
		this.minutes = minutes;
	}

	/** 모든 구간의 이동 시간. null 이면 이동 시간을 모른다. 호출 기록도 지운다. 각 테스트 시작에 부른다. */
	public void willReturn(Integer minutes) {
		this.minutes = minutes;
		this.calls.clear();
	}

	public List<TransportMode> calls() {
		return List.copyOf(calls);
	}

	@Override
	public TravelLeg estimate(GeoPoint from, GeoPoint to, TransportMode mode) {
		calls.add(mode);
		if (minutes == null) {
			return TravelLeg.unavailable(SOURCE, "FAKE_UNAVAILABLE", AS_OF);
		}
		return TravelLeg.found(minutes, new BigDecimal("1000.00"), SOURCE, AS_OF);
	}
}
