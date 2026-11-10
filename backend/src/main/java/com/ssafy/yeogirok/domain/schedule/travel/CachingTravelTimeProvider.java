package com.ssafy.yeogirok.domain.schedule.travel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/**
 * 구간 이동 시간을 잠시 기억해 같은 구간을 다시 묻지 않는다. 일정 편집·재계산은 바뀌지 않은 구간도 다시 계산하고, 대중교통 응답은
 * 경로 좌표까지 담겨 커서(구간마다 수십 KB) 같은 구간 재조회를 줄인다.
 *
 * <ul>
 *   <li>키: 출발·도착 좌표(끝자리 0 을 지운 값)와 이동 수단</li>
 *   <li>이동 시간을 구한 결과만 기억한다. 모르는 결과(실패·경로 없음·키 없음)는 다음에 다시 묻는다</li>
 *   <li>{@link #TTL} 이 지나면 버리고, {@link #MAX_ENTRIES} 를 넘으면 가장 오래 안 쓴 것부터 버린다</li>
 *   <li>돌려주는 값의 asOf 는 처음 조회한 시각 그대로다(값의 근거 시각)</li>
 * </ul>
 * 서버 한 대의 메모리 캐시다. 서버를 다시 띄우면 비워진다.
 */
public class CachingTravelTimeProvider implements TravelTimeProvider {

	/** 기억하는 시간. 출발 시각을 넣지 않는 조회라 시간대별 차이는 원래 반영하지 않는다(제안값). */
	static final Duration TTL = Duration.ofHours(6);
	/** 기억하는 구간 수 상한(제안값). */
	static final int MAX_ENTRIES = 10_000;

	private final TravelTimeProvider delegate;
	private final Clock clock;
	private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, Entry> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	public CachingTravelTimeProvider(TravelTimeProvider delegate, Clock clock) {
		this.delegate = delegate;
		this.clock = clock;
	}

	@Override
	public TravelLeg estimate(GeoPoint from, GeoPoint to, TransportMode mode) {
		Key key = new Key(normalize(from.latitude()), normalize(from.longitude()), normalize(to.latitude()),
			normalize(to.longitude()), mode);
		Instant now = clock.instant();
		synchronized (entries) {
			Entry cached = entries.get(key);
			if (cached != null && now.isBefore(cached.expiresAt())) {
				return cached.leg();
			}
			entries.remove(key);
		}
		// 외부 호출은 잠금 밖에서 한다. 같은 구간을 동시에 물으면 둘 다 부를 수 있지만 결과는 같다.
		TravelLeg leg = delegate.estimate(from, to, mode);
		if (leg.isKnown()) {
			synchronized (entries) {
				entries.put(key, new Entry(leg, now.plus(TTL)));
			}
		}
		return leg;
	}

	private static BigDecimal normalize(BigDecimal value) {
		return value.stripTrailingZeros();
	}

	private record Key(BigDecimal fromLatitude, BigDecimal fromLongitude, BigDecimal toLatitude,
		BigDecimal toLongitude, TransportMode mode) {
	}

	private record Entry(TravelLeg leg, Instant expiresAt) {
	}
}
