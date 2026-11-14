package com.ssafy.yeogirok.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ssafy.yeogirok.domain.place.tour.TourApiClient;
import com.ssafy.yeogirok.domain.place.tour.TourApiException;
import com.ssafy.yeogirok.domain.place.tour.TourAreaQuery;
import com.ssafy.yeogirok.domain.place.tour.TourKeywordQuery;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.domain.place.tour.TourSearchPage;

/**
 * 가짜 TourAPI. 지역 조회는 분류 조건(lclsSystm1[/lclsSystm2])별, 키워드 검색은 검색어별, 상세는 콘텐츠 ID 별로 정해 둔 결과를
 * 준다. 정하지 않은 조건은 빈 결과다. 추천 서비스가 동시에 부르므로 상태는 스레드 안전한 컬렉션에 둔다.
 */
public class FakeTourApiClient implements TourApiClient {

	private final Map<String, List<TourPlace>> results = new ConcurrentHashMap<>();
	private final Set<String> failing = ConcurrentHashMap.newKeySet();
	private final Map<String, Duration> delays = new ConcurrentHashMap<>();
	private final List<TourAreaQuery> calls = new CopyOnWriteArrayList<>();
	private final Map<String, TourSearchPage> keywordResults = new ConcurrentHashMap<>();
	private final List<TourKeywordQuery> keywordCalls = new CopyOnWriteArrayList<>();
	private final Map<String, TourPlace> details = new ConcurrentHashMap<>();
	private final List<String> detailCalls = new CopyOnWriteArrayList<>();
	private volatile TourApiException searchFailure;
	private volatile Duration detailDelay = Duration.ZERO;

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

	/** 이 검색어로 찾으면 쪽 번호와 관계없이 이 결과를 준다. */
	public void willFind(String keyword, int totalCount, TourPlace... places) {
		keywordResults.put(keyword, new TourSearchPage(List.of(places), totalCount));
	}

	/** 상세 조회에 이 장소를 준다. */
	public void willDetail(TourPlace place) {
		details.put(place.contentId(), place);
	}

	/** 키워드 검색·상세 조회가 모두 실패한다. */
	public void willFailSearch() {
		searchFailure = new TourApiException("가짜 실패");
	}

	/** 키워드 검색·상세 조회를 TourAPI 가 파라미터 오류(결과 코드 10)로 거절한다. */
	public void willRejectSearch() {
		searchFailure = new TourApiException("가짜 파라미터 오류", "10");
	}

	public void reset() {
		results.clear();
		failing.clear();
		delays.clear();
		calls.clear();
		keywordResults.clear();
		keywordCalls.clear();
		details.clear();
		detailCalls.clear();
		searchFailure = null;
		detailDelay = Duration.ZERO;
	}

	public List<TourAreaQuery> calls() {
		return List.copyOf(calls);
	}

	public List<TourKeywordQuery> keywordCalls() {
		return List.copyOf(keywordCalls);
	}

	public List<String> detailCalls() {
		return List.copyOf(detailCalls);
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

	@Override
	public TourSearchPage searchKeyword(TourKeywordQuery query) {
		keywordCalls.add(query);
		if (searchFailure != null) {
			throw searchFailure;
		}
		return keywordResults.getOrDefault(query.keyword(), new TourSearchPage(List.of(), 0));
	}

	/** 상세 조회가 응답 전에 기다린다(마감 시간 확인용). */
	public void willDelayDetail(Duration delay) {
		detailDelay = delay;
	}

	@Override
	public Optional<TourPlace> detail(String contentId) {
		detailCalls.add(contentId);
		if (!detailDelay.isZero()) {
			try {
				Thread.sleep(detailDelay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new TourApiException("가짜 호출 중단");
			}
		}
		if (searchFailure != null) {
			throw searchFailure;
		}
		return Optional.ofNullable(details.get(contentId));
	}
}
