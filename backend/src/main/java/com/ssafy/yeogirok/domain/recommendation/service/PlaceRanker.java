package com.ssafy.yeogirok.domain.recommendation.service;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.global.geo.GeoDistance;

/**
 * 장소 추천 규칙 점수(API-034). 스프링·DB 없이 계산만 한다.
 *
 * <ol>
 *   <li>동선 점수: 담은 후보 중심점과의 직선거리. {@code 1 - 거리/기준거리}(0 미만은 0). 중심점이 없으면 쓰지 않는다.</li>
 *   <li>인기 점수: 최근 기간에 이 장소를 담은 여행방 수. 후보 중 최댓값 대비 로그 비율(0~1).</li>
 *   <li>합계: 중심점이 있으면 {@code 동선 0.7 + 인기 0.3}, 없으면 인기만. 같으면 TourAPI 순서(최근 수정순)를 지킨다.</li>
 *   <li>카테고리마다 점수순으로 줄을 세우고 선택한 카테고리 순서대로 한 개씩 번갈아 뽑는다(한 카테고리 독차지 방지).</li>
 * </ol>
 * 이유 문장은 점수에 쓴 근거만으로 만든다. 근거가 없는 문구(예: 인기 0)는 넣지 않는다.
 */
public final class PlaceRanker {

	static final double DISTANCE_WEIGHT = 0.7;
	static final double POPULARITY_WEIGHT = 0.3;
	private static final String SEPARATOR = " · ";

	private PlaceRanker() {
	}

	/**
	 * @param candidates     카테고리별 후보. 카테고리는 사용자가 고른 순서, 후보는 TourAPI 순서다. 같은 장소가 여러
	 *                       카테고리에 있으면 처음 것만 쓴다
	 * @param center         담은 후보 좌표의 중심점. 담은 후보가 없으면 null
	 * @param radiusMeters   동선 점수 기준거리
	 * @param popularity     contentId → 담은 여행방 수. 없으면 0
	 * @param popularDays    인기 집계 기간(일). 문구에 쓴다
	 * @param categoryNames  카테고리 코드 → 표시명. 없으면 이름 없이 쓴다
	 */
	public static List<RankedPlace> rank(Map<String, List<TourPlace>> candidates, Center center, int radiusMeters,
		Map<String, Long> popularity, long popularDays, Map<String, String> categoryNames, int limit) {
		long maxPopularity = popularity.values().stream().mapToLong(Long::longValue).max().orElse(0);

		Set<String> seen = new HashSet<>();
		Map<String, Deque<Scored>> queues = new LinkedHashMap<>();
		candidates.forEach((categoryCode, places) -> {
			List<Scored> scored = new ArrayList<>();
			for (TourPlace place : places) {
				if (!seen.add(place.contentId())) {
					continue;
				}
				Double distance = center == null ? null : GeoDistance.meters(center.latitude(), center.longitude(),
					place.latitude(), place.longitude());
				long rooms = popularity.getOrDefault(place.contentId(), 0L);
				scored.add(new Scored(categoryCode, place, distance, rooms,
					score(distance, radiusMeters, rooms, maxPopularity)));
			}
			// 정렬은 안정 정렬이라 점수가 같으면 TourAPI 순서가 남는다.
			scored.sort(Comparator.comparingDouble(Scored::score).reversed());
			queues.put(categoryCode, new ArrayDeque<>(scored));
		});

		List<RankedPlace> ranked = new ArrayList<>();
		while (ranked.size() < limit && queues.values().stream().anyMatch(queue -> !queue.isEmpty())) {
			for (Deque<Scored> queue : queues.values()) {
				if (ranked.size() >= limit) {
					break;
				}
				Scored next = queue.pollFirst();
				if (next != null) {
					ranked.add(next.toRanked(ranked.size() + 1, popularDays, categoryNames));
				}
			}
		}
		return ranked;
	}

	static double score(Double distanceMeters, int radiusMeters, long rooms, long maxPopularity) {
		double popularityScore = maxPopularity == 0 ? 0 : Math.log1p(rooms) / Math.log1p(maxPopularity);
		if (distanceMeters == null) {
			return popularityScore;
		}
		double distanceScore = Math.max(0, 1 - distanceMeters / radiusMeters);
		return DISTANCE_WEIGHT * distanceScore + POPULARITY_WEIGHT * popularityScore;
	}

	/** 350m(10m 단위 반올림), 1.2km(소수 첫째 자리). 반올림해 1000m 가 되면 km 로 쓴다. */
	static String formatDistance(double meters) {
		long rounded = Math.round(meters / 10) * 10;
		if (rounded < 1000) {
			return rounded + "m";
		}
		return String.format(Locale.ROOT, "%.1fkm", meters / 1000);
	}

	/** 좌표 한 점. 담은 후보의 중심점으로 쓴다. */
	public record Center(BigDecimal latitude, BigDecimal longitude) {

		/** 점들의 중심점(위도·경도 평균). 추천 지역 안의 짧은 거리라 평균으로 충분하다. 점이 없으면 null. */
		public static Center average(List<Center> points) {
			if (points.isEmpty()) {
				return null;
			}
			double lat = points.stream().mapToDouble(point -> point.latitude().doubleValue()).average().orElseThrow();
			double lng = points.stream().mapToDouble(point -> point.longitude().doubleValue()).average().orElseThrow();
			return new Center(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
		}
	}

	/** 순위가 정해진 장소. evidence 는 reason 을 만든 근거 목록이다. */
	public record RankedPlace(int rank, String categoryCode, TourPlace place, String reason, List<String> evidence) {
	}

	private record Scored(String categoryCode, TourPlace place, Double distance, long rooms, double score) {

		RankedPlace toRanked(int rank, long popularDays, Map<String, String> categoryNames) {
			String categoryName = categoryNames.get(categoryCode);
			List<String> evidence = new ArrayList<>();
			List<String> reasonParts = new ArrayList<>();
			evidence.add(categoryName == null ? "카테고리 일치" : "카테고리 일치: " + categoryName);
			reasonParts.add(categoryName == null ? "선택한 카테고리" : categoryName);
			if (distance != null) {
				String near = "담은 장소들과 " + formatDistance(distance);
				evidence.add(near);
				reasonParts.add(near);
			}
			if (rooms > 0) {
				evidence.add("최근 %d일 여행방 %d곳이 담음".formatted(popularDays, rooms));
				reasonParts.add("최근 %d일 여행방 %d곳이 담았어요".formatted(popularDays, rooms));
			}
			return new RankedPlace(rank, categoryCode, place, String.join(SEPARATOR, reasonParts), List.copyOf(evidence));
		}
	}
}
