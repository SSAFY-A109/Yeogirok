package com.ssafy.yeogirok.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 서버 현재 시각을 정하고 옮기는 시계. */
public class MutableClock extends Clock {

	private Instant now;

	public MutableClock(Instant now) {
		this.now = now;
	}

	public void setInstant(Instant now) {
		this.now = now;
	}

	public void advance(Duration duration) {
		this.now = now.plus(duration);
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}
}
