package com.ssafy.yeogirok.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ssafy.yeogirok.domain.place.tour.TourApiClient;
import com.ssafy.yeogirok.domain.place.tour.TourApiException;
import com.ssafy.yeogirok.domain.place.tour.TourAreaQuery;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;

/**
 * 분류 조건(lclsSystm1[/lclsSystm2])별로 정해 둔 결과를 주는 가짜 TourAPI. 정하지 않은 조건은 빈 목록이다.
 * 추천 서비스가 동시에 부르므로 상태는 스레드 안전한 컬렉션에 둔다.
 */
public class FakeTourApiClient implements TourApiClient {

	private final Map<String, List<TourPlace>> results = new ConcurrentHashMap<>();
	private final Set<String> failing = ConcurrentHashMap.newKeySet();
	private final Map<String, Duration> delays = new ConcurrentHashMap<>();
	private final List<TourAreaQuery> calls = new CopyOnWriteArrayList<>();

	public void willReturn(String classification, TourPlace... places) {
		results.put(classification, List.of(places));
	}

	public void willFail(String... classifications) {
		failing.addAll(List.of(classifications));
	}

	/** 응답 전에 기다린다(마감 시간 확인용). */
	public void willDelay(String classification, Duration delay) {
		delays.put(classification, delay);
	}

	public void reset() {
		results.clear();
		failing.clear();
		delays.clear();
		calls.clear();
	}

	public List<TourAreaQuery> calls() {
		return List.copyOf(calls);
	}

	@Override
	public List<TourPlace> areaBasedList(TourAreaQuery query) {
		calls.add(query);
		String classification = query.lclsSystm2() == null ? query.lclsSystm1()
			: query.lclsSystm1() + "/" + query.lclsSystm2();
		Duration delay = delays.get(classification);
		if (delay != null) {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new TourApiException("가짜 호출 중단");
			}
		}
		if (failing.contains(classification)) {
			throw new TourApiException("가짜 실패");
		}
		return results.getOrDefault(classification, List.of());
	}
}
