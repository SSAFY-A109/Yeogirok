package com.ssafy.yeogirok.domain.schedule.travel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.support.FakeTravelTimeProvider;
import com.ssafy.yeogirok.support.MutableClock;

class CachingTravelTimeProviderTest {

	static final GeoPoint A = new GeoPoint(new BigDecimal("37.5000000"), new BigDecimal("127.0300000"));
	static final GeoPoint A_SHORT = new GeoPoint(new BigDecimal("37.5"), new BigDecimal("127.03"));
	static final GeoPoint B = new GeoPoint(new BigDecimal("37.6000000"), new BigDecimal("127.0300000"));
	static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

	final FakeTravelTimeProvider delegate = new FakeTravelTimeProvider(20);
	final MutableClock clock = new MutableClock(NOW);
	final CachingTravelTimeProvider cache = new CachingTravelTimeProvider(delegate, clock);

	@Test
	void 같은_구간과_이동_수단은_한_번만_묻는다() {
		TravelLeg first = cache.estimate(A, B, TransportMode.CAR);
		TravelLeg again = cache.estimate(A_SHORT, B, TransportMode.CAR);

		assertThat(again).isEqualTo(first);
		assertThat(delegate.calls()).hasSize(1);
	}

	@Test
	void 방향이나_이동_수단이_다르면_따로_묻는다() {
		cache.estimate(A, B, TransportMode.CAR);
		cache.estimate(B, A, TransportMode.CAR);
		cache.estimate(A, B, TransportMode.TRANSIT);

		assertThat(delegate.calls()).hasSize(3);
	}

	@Test
	void 기억하는_시간이_지나면_다시_묻는다() {
		cache.estimate(A, B, TransportMode.CAR);
		clock.set(NOW.plus(CachingTravelTimeProvider.TTL));

		cache.estimate(A, B, TransportMode.CAR);

		assertThat(delegate.calls()).hasSize(2);
	}

	@Test
	void 이동_시간을_모르는_결과는_기억하지_않는다() {
		delegate.willReturn(null);
		cache.estimate(A, B, TransportMode.CAR);
		delegate.willReturn(20);

		TravelLeg leg = cache.estimate(A, B, TransportMode.CAR);

		assertThat(leg.travelMinutes()).isEqualTo(20);
		assertThat(delegate.calls()).hasSize(1);
	}
}
