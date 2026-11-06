package com.ssafy.yeogirok.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;

/** 실제 길찾기를 부르지 않고 정해 둔 이동 시간을 돌려준다. 받은 이동 수단을 기록한다. */
public class FakeTravelTimeProvider implements TravelTimeProvider {

	public static final String SOURCE = "FAKE";
	public static final Instant AS_OF = Instant.parse("2026-11-05T00:00:00Z");

	private volatile Integer minutes;
	private final List<TransportMode> calls = new CopyOnWriteArrayList<>();
	private volatile Pause pause;

	public FakeTravelTimeProvider(Integer minutes) {
		this.minutes = minutes;
	}

	/** 모든 구간의 이동 시간. null 이면 이동 시간을 모른다. 호출 기록·멈춤도 지운다. 각 테스트 시작에 부른다. */
	public void willReturn(Integer minutes) {
		this.minutes = minutes;
		this.calls.clear();
		this.pause = null;
	}

	/**
	 * 다음 호출을 멈춘다. 동시성 테스트에서 "계산 중"에 다른 요청을 끼워 넣을 때 쓴다.
	 *
	 * @param entered 멈춘 호출이 들어오면 내려간다
	 * @param release 이것이 내려가야 호출이 끝난다
	 */
	public void pauseNextCall(CountDownLatch entered, CountDownLatch release) {
		this.pause = new Pause(entered, release);
	}

	public List<TransportMode> calls() {
		return List.copyOf(calls);
	}

	@Override
	public TravelLeg estimate(GeoPoint from, GeoPoint to, TransportMode mode) {
		calls.add(mode);
		Pause current = pause;
		if (current != null) {
			pause = null;
			current.entered().countDown();
			try {
				if (!current.release().await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("멈춘 길찾기 호출을 10초 안에 풀지 않았습니다.");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}
		if (minutes == null) {
			return TravelLeg.unavailable(SOURCE, "FAKE_UNAVAILABLE", AS_OF);
		}
		return TravelLeg.found(minutes, new BigDecimal("1000.00"), SOURCE, AS_OF);
	}

	private record Pause(CountDownLatch entered, CountDownLatch release) {
	}
}
