package com.ssafy.yeogirok.domain.schedule.draft;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 일정 초안의 날짜 배정과 날짜 안 방문 순서를 정한다(API-037). 시각은 정하지 않는다. 결과 순서로 시각을 계산하는 것은
 * {@code ScheduleCalculator} 다.
 *
 * <ol>
 *   <li><b>날짜 나누기</b>: 이어받은 항목(fixedItems)은 그 날짜에 둔다. 나머지는 직선 거리로 가까운 것끼리 날짜 수만큼
 *   묶는다. 하루에 쓸 수 있는 시간(체류 + 예상 이동)을 넘기지 않게 넣는다.</li>
 *   <li><b>맛집 나누기</b>: 맛집(FOOD)이 모자라면 모든 날의 점심을 먼저 채우고 남는 맛집으로 저녁을 채운다. 날마다
 *   필요한 수만큼 맛집이 남는 날에서 가까운 맛집을 옮긴다.</li>
 *   <li><b>날짜 안 순서</b>: 그날 장소 사이 이동 시간을 길찾기로 구해(캐시 사용) 이동 합계 + 식사·종료 시각 벌점이 가장
 *   작은 순서를 고른다. {@value #EXACT_LIMIT}곳 이하는 순서를 모두 비교하고, 그보다 많으면 가까운 곳부터 잇고 2-opt 로
 *   다듬는다. 순서 고정(fixed) 항목은 받은 순서 자리에 둔다.</li>
 *   <li><b>시간 넘김</b>: 종료 시각을 넘기면 고정하지 않은 항목을 하나씩 빼(미배치) 시간 안에 들어오게 한다. 고정
 *   항목만으로도 넘치고 그날 이동 시간을 모두 알면 SC002 다.</li>
 * </ol>
 *
 * <p>이동 시간을 모르는 구간은 순서·배정을 고를 때만 직선 거리로 어림한다. 어림값은 결과 시각에 쓰지 않는다.
 * 식사 시간대에 맞추려고 기다리는 시간을 넣지 않는다.
 */
public final class DraftPlanner {

	/** 순서를 모두 비교하는 최대 장소 수(명세: 10곳 이하 순서 비교). */
	static final int EXACT_LIMIT = 10;
	/** 날짜를 나눌 때 장소마다 더하는 예상 이동 시간(분). */
	static final int ASSUMED_TRAVEL_MINUTES = 20;

	static final LocalTime LUNCH_FROM = LocalTime.of(11, 0);
	static final LocalTime LUNCH_TO = LocalTime.of(14, 0);
	static final LocalTime DINNER_FROM = LocalTime.of(17, 0);
	static final LocalTime DINNER_TO = LocalTime.of(20, 0);
	static final Duration MEAL_GAP = Duration.ofHours(5);

	/** 벌점(분 단위 비용). 이동 합계보다 크게 두어 식사·종료 시각을 먼저 맞춘다. */
	static final int LUNCH_MISSED = 600;
	static final int DINNER_MISSED = 400;
	static final int OVERFLOW_PER_MINUTE = 20;

	private DraftPlanner() {
	}

	/**
	 * 배치할 항목 하나.
	 *
	 * @param fixedDate  이어받은 항목의 날짜. 없으면 null(자유롭게 배정)
	 * @param fixedOrder 순서 고정 항목의 순서. 고정이 아니면 null
	 */
	public record Candidate(int itemId, GeoPoint location, boolean food, int stayMinutes, LocalDate fixedDate,
		Integer fixedOrder) {

		boolean orderFixed() {
			return fixedOrder != null;
		}
	}

	/**
	 * @param days       여행의 모든 일차(날짜 순)
	 * @param startPoint 첫날 출발지. 없으면 null
	 * @param endPoint   마지막 날 도착지. 없으면 null
	 */
	public record Input(List<DayOption> days, GeoPoint startPoint, GeoPoint endPoint, List<Candidate> candidates,
		TransportMode mode) {
	}

	/**
	 * @param orders     날짜별 itemId 방문 순서
	 * @param unassigned 넣지 못한 itemId
	 */
	public record Output(Map<LocalDate, List<Integer>> orders, List<Integer> unassigned) {
	}

	public static Output plan(Input input, TravelTimeProvider provider) {
		Map<LocalDate, List<Candidate>> byDay = assignDays(input);
		balanceMeals(input, byDay);

		Map<LocalDate, List<Integer>> orders = new LinkedHashMap<>();
		List<Integer> unassigned = new ArrayList<>();
		LocalDate first = input.days().getFirst().date();
		LocalDate last = input.days().getLast().date();
		for (DayOption day : input.days()) {
			GeoPoint start = day.date().equals(first) ? input.startPoint() : null;
			GeoPoint end = day.date().equals(last) ? input.endPoint() : null;
			DayPlan plan = new DayPlan(day, start, end, byDay.get(day.date()), input.mode(), provider);
			List<Candidate> ordered = plan.orderAndTrim(unassigned);
			orders.put(day.date(), ordered.stream().map(Candidate::itemId).toList());
		}
		unassigned.sort(Comparator.naturalOrder());
		return new Output(orders, unassigned);
	}

	/* ── 날짜 나누기 ─────────────────────────────────────────── */

	private static Map<LocalDate, List<Candidate>> assignDays(Input input) {
		List<DayOption> days = input.days();
		Map<LocalDate, List<Candidate>> fixedByDay = new LinkedHashMap<>();
		days.forEach(day -> fixedByDay.put(day.date(), new ArrayList<>()));
		List<Candidate> free = new ArrayList<>();
		for (Candidate candidate : input.candidates()) {
			if (candidate.fixedDate() != null) {
				fixedByDay.get(candidate.fixedDate()).add(candidate);
			} else {
				free.add(candidate);
			}
		}

		Map<LocalDate, double[]> centers = seedCenters(input, fixedByDay, free);
		// 날짜마다 쓸 수 있는 시간에 비례한 장소 수 상한. 가까운 곳이 한쪽에 몰려도 한 날에 다 넣지 않는다.
		long totalCapacity = days.stream().mapToLong(DraftPlanner::capacity).sum();
		Map<LocalDate, Integer> quota = new HashMap<>();
		for (DayOption day : days) {
			quota.put(day.date(), (int)Math.ceil((double)input.candidates().size() * capacity(day) / totalCapacity));
		}
		Map<LocalDate, List<Candidate>> byDay = new LinkedHashMap<>();
		for (int round = 0; round < 10; round++) {
			byDay = new LinkedHashMap<>();
			Map<LocalDate, Integer> load = new HashMap<>();
			for (DayOption day : days) {
				List<Candidate> list = new ArrayList<>(fixedByDay.get(day.date()));
				byDay.put(day.date(), list);
				load.put(day.date(), list.stream().mapToInt(DraftPlanner::cost).sum());
			}
			Map<LocalDate, double[]> currentCenters = centers;
			// 중심에서 먼 항목부터 넣어 가까운 항목이 남는 자리를 차지하게 한다.
			List<Candidate> ordered = new ArrayList<>(free);
			ordered.sort(Comparator.comparingDouble(c -> -nearestDistance(c.location(), currentCenters)));
			for (Candidate candidate : ordered) {
				// 1순위: 상한·시간 안에서 가장 가까운 날. 2순위: 시간 안에서 가장 가까운 날. 없으면 가장 가까운 날.
				LocalDate withinQuota = null;
				double withinQuotaDistance = Double.MAX_VALUE;
				LocalDate fits = null;
				double fitsDistance = Double.MAX_VALUE;
				LocalDate nearest = null;
				double nearestDistance = Double.MAX_VALUE;
				for (DayOption day : days) {
					double distance = distanceKm(candidate.location(), centers.get(day.date()));
					if (distance < nearestDistance) {
						nearestDistance = distance;
						nearest = day.date();
					}
					boolean fitsTime = load.get(day.date()) + cost(candidate) <= capacity(day);
					if (fitsTime && distance < fitsDistance) {
						fitsDistance = distance;
						fits = day.date();
					}
					if (fitsTime && byDay.get(day.date()).size() < quota.get(day.date())
						&& distance < withinQuotaDistance) {
						withinQuotaDistance = distance;
						withinQuota = day.date();
					}
				}
				LocalDate target = withinQuota != null ? withinQuota : fits != null ? fits : nearest;
				byDay.get(target).add(candidate);
				load.merge(target, cost(candidate), Integer::sum);
			}
			Map<LocalDate, double[]> next = new LinkedHashMap<>();
			byDay.forEach((date, list) -> next.put(date, list.isEmpty() ? currentCenters.get(date) : centroid(list)));
			if (sameCenters(centers, next)) {
				break;
			}
			centers = next;
		}
		return byDay;
	}

	/** 날짜별 시작 중심. 이어받은 항목이 있으면 그 중심, 없으면 이미 고른 중심에서 가장 먼 자유 항목. */
	private static Map<LocalDate, double[]> seedCenters(Input input, Map<LocalDate, List<Candidate>> fixedByDay,
		List<Candidate> free) {
		Map<LocalDate, double[]> centers = new LinkedHashMap<>();
		fixedByDay.forEach((date, list) -> {
			if (!list.isEmpty()) {
				centers.put(date, centroid(list));
			}
		});
		List<Candidate> pool = new ArrayList<>(free);
		for (DayOption day : input.days()) {
			if (centers.containsKey(day.date())) {
				continue;
			}
			if (pool.isEmpty()) {
				centers.put(day.date(), centers.isEmpty() ? new double[] {0, 0} : centers.values().iterator().next());
				continue;
			}
			Candidate seed;
			if (centers.isEmpty()) {
				// 첫 중심: 첫날 출발지에서 가장 가까운 항목, 출발지가 없으면 전체 중심에서 가장 먼 항목
				GeoPoint start = input.startPoint();
				double[] all = centroid(pool);
				seed = start != null
					? pool.stream().min(Comparator.comparingDouble(c -> distanceKm(c.location(), start))).orElseThrow()
					: pool.stream().max(Comparator.comparingDouble(c -> distanceKm(c.location(), all))).orElseThrow();
			} else {
				seed = pool.stream().max(Comparator.comparingDouble(c -> nearestDistance(c.location(), centers)))
					.orElseThrow();
			}
			pool.remove(seed);
			centers.put(day.date(), point(seed.location()));
		}
		return centers;
	}

	/* ── 맛집 나누기 ─────────────────────────────────────────── */

	/** 점심 → 저녁 순서로, 맛집이 필요한 날에 맛집이 남는 날의 자유 맛집을 옮긴다. */
	private static void balanceMeals(Input input, Map<LocalDate, List<Candidate>> byDay) {
		for (int meal = 1; meal <= 2; meal++) {
			for (DayOption day : input.days()) {
				int need = mealsCovered(day, meal);
				List<Candidate> list = byDay.get(day.date());
				while (foods(list) < need) {
					Candidate moved = takeSpareFood(input, byDay, day, meal, list);
					if (moved == null) {
						break;
					}
					list.add(moved);
				}
			}
		}
	}

	/** 그날 시간대가 끼니를 포함하면 meal 번째 끼니까지 맛집이 필요하다(1: 점심, 2: 저녁). */
	private static int mealsCovered(DayOption day, int meal) {
		int count = 0;
		if (overlaps(day, LUNCH_FROM, LUNCH_TO)) {
			count++;
		}
		if (meal >= 2 && overlaps(day, DINNER_FROM, DINNER_TO)) {
			count++;
		}
		return count;
	}

	/** 다른 날 중 필요보다 맛집이 많은 날에서 이 날 중심에 가장 가까운 자유 맛집을 빼 온다. 없으면 null. */
	private static Candidate takeSpareFood(Input input, Map<LocalDate, List<Candidate>> byDay, DayOption target,
		int meal, List<Candidate> targetList) {
		double[] center = targetList.isEmpty() ? null : centroid(targetList);
		Candidate best = null;
		LocalDate bestDay = null;
		double bestDistance = Double.MAX_VALUE;
		for (DayOption other : input.days()) {
			if (other.date().equals(target.date())) {
				continue;
			}
			List<Candidate> list = byDay.get(other.date());
			if (foods(list) <= mealsCovered(other, meal)) {
				continue;
			}
			for (Candidate candidate : list) {
				if (!candidate.food() || candidate.fixedDate() != null) {
					continue;
				}
				double distance = center == null ? 0 : distanceKm(candidate.location(), center);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = candidate;
					bestDay = other.date();
				}
			}
		}
		if (best != null) {
			byDay.get(bestDay).remove(best);
		}
		return best;
	}

	private static long foods(List<Candidate> list) {
		return list.stream().filter(Candidate::food).count();
	}

	/* ── 날짜 안 순서 ─────────────────────────────────────────── */

	/** 하루의 순서 고르기. 이동 시간은 처음 한 번 길찾기로 구해 둔다. */
	private static final class DayPlan {

		private final DayOption day;
		private final GeoPoint start;
		private final GeoPoint end;
		private final TransportMode mode;
		private final TravelTimeProvider provider;
		private final List<Candidate> items;
		private final Map<Pair, Integer> minutes = new HashMap<>();
		private final Map<Pair, Boolean> known = new HashMap<>();
		private final long startMinute;
		private final long endMinute;

		DayPlan(DayOption day, GeoPoint start, GeoPoint end, List<Candidate> items, TransportMode mode,
			TravelTimeProvider provider) {
			this.day = day;
			this.start = start;
			this.end = end;
			this.items = new ArrayList<>(items);
			this.mode = mode;
			this.provider = provider;
			this.startMinute = day.startTime().toSecondOfDay() / 60;
			this.endMinute = day.endTime().toSecondOfDay() / 60;
		}

		/** 고른 순서. 시간 안에 못 넣어 뺀 항목은 unassigned 에 더한다. */
		List<Candidate> orderAndTrim(List<Integer> unassigned) {
			List<Candidate> best = order(items);
			while (overflow(best) > 0) {
				Candidate drop = dropCandidate(best);
				if (drop == null) {
					if (allKnown(best)) {
						throw new BusinessException(ExceptionType.SCHEDULE_UNSATISFIABLE,
							"%s: 순서를 고정한 항목만으로도 종료 시각을 넘깁니다.".formatted(day.date()));
					}
					break;
				}
				items.remove(drop);
				unassigned.add(drop.itemId());
				best = order(items);
			}
			return best;
		}

		private List<Candidate> order(List<Candidate> candidates) {
			if (candidates.size() <= 1) {
				return List.copyOf(candidates);
			}
			return candidates.size() <= EXACT_LIMIT ? exact(candidates) : improved(candidates);
		}

		/*
		 * 모든 순서 비교(분기 한정). 이동 시간은 먼저 번호 행렬로 만들어 두고 번호로만 찾는다. 근사 해로 처음 상한을 잡고,
		 * "지금까지 이동 + 아직 놓지 않은 장소마다 가장 짧게 들어오는 이동"이 상한 이상이면 더 보지 않는다(벌점은 0 이상).
		 */
		private List<Candidate> exact(List<Candidate> candidates) {
			int size = candidates.size();
			long[][] matrix = new long[size][size];
			long[] fromStart = new long[size];
			long[] toEnd = new long[size];
			long[] stays = new long[size];
			boolean[] foods = new boolean[size];
			long[] minIn = new long[size];
			for (int i = 0; i < size; i++) {
				GeoPoint location = candidates.get(i).location();
				fromStart[i] = travel(start, location);
				toEnd[i] = travel(location, end);
				stays[i] = candidates.get(i).stayMinutes();
				foods[i] = candidates.get(i).food();
				for (int j = 0; j < size; j++) {
					matrix[i][j] = i == j ? 0 : travel(location, candidates.get(j).location());
				}
			}
			long bound = 0;
			for (int to = 0; to < size; to++) {
				long min = start == null ? Long.MAX_VALUE : fromStart[to];
				for (int from = 0; from < size; from++) {
					if (from != to) {
						min = Math.min(min, matrix[from][to]);
					}
				}
				minIn[to] = min == Long.MAX_VALUE ? 0 : min;
				bound += minIn[to];
			}

			int[] slots = new int[size];
			java.util.Arrays.fill(slots, -1);
			List<Integer> loose = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				Candidate candidate = candidates.get(i);
				int index = candidate.orderFixed() ? Math.min(candidate.fixedOrder(), size) - 1 : -1;
				if (index >= 0 && slots[index] < 0) {
					slots[index] = i;
				} else {
					loose.add(i);
				}
			}

			IndexSearch search = new IndexSearch(matrix, fromStart, toEnd, stays, foods, minIn, slots,
				loose.stream().mapToInt(Integer::intValue).toArray());
			List<Candidate> seed = improved(candidates);
			search.best = seed.stream().mapToInt(candidates::indexOf).toArray();
			search.bestCost = cost(seed);
			search.run(0, -1, 0, bound, new boolean[search.loose.length]);
			return java.util.Arrays.stream(search.best).mapToObj(candidates::get).toList();
		}

		private final class IndexSearch {
			final long[][] matrix;
			final long[] fromStart;
			final long[] toEnd;
			final long[] stays;
			final boolean[] foods;
			final long[] minIn;
			final int[] slots;
			final int[] loose;
			final int[] current;
			final long[] arrivals;
			final boolean[] orderFoods;
			int[] best;
			long bestCost;

			IndexSearch(long[][] matrix, long[] fromStart, long[] toEnd, long[] stays, boolean[] foods, long[] minIn,
				int[] slots, int[] loose) {
				this.matrix = matrix;
				this.fromStart = fromStart;
				this.toEnd = toEnd;
				this.stays = stays;
				this.foods = foods;
				this.minIn = minIn;
				this.slots = slots;
				this.loose = loose;
				this.current = new int[slots.length];
				this.arrivals = new long[slots.length];
				this.orderFoods = new boolean[slots.length];
			}

			void run(int index, int previous, long travel, long remaining, boolean[] used) {
				if (travel + remaining >= bestCost) {
					return;
				}
				if (index == current.length) {
					long cost = leafCost();
					if (cost < bestCost) {
						bestCost = cost;
						best = current.clone();
					}
					return;
				}
				if (slots[index] >= 0) {
					place(index, previous, travel, remaining, used, slots[index]);
					return;
				}
				for (int i = 0; i < loose.length; i++) {
					if (used[i]) {
						continue;
					}
					used[i] = true;
					place(index, previous, travel, remaining, used, loose[i]);
					used[i] = false;
				}
			}

			private void place(int index, int previous, long travel, long remaining, boolean[] used, int node) {
				current[index] = node;
				long leg = previous < 0 ? fromStart[node] : matrix[previous][node];
				run(index + 1, node, travel + leg, remaining - minIn[node], used);
			}

			private long leafCost() {
				long clock = startMinute;
				long travel = 0;
				int previous = -1;
				for (int i = 0; i < current.length; i++) {
					int node = current[i];
					long leg = previous < 0 ? fromStart[node] : matrix[previous][node];
					travel += leg;
					clock += leg;
					arrivals[i] = clock;
					clock += stays[node];
					orderFoods[i] = foods[node];
					previous = node;
				}
				long tail = toEnd[previous];
				return score(arrivals, orderFoods, travel + tail, clock + tail);
			}
		}

		/* 많을 때: 가까운 곳부터 잇고 고정 자리를 맞춘 뒤 2-opt 로 비용을 줄인다. */
		private List<Candidate> improved(List<Candidate> candidates) {
			List<Candidate> remaining = new ArrayList<>(candidates);
			List<Candidate> route = new ArrayList<>();
			GeoPoint previous = start;
			while (!remaining.isEmpty()) {
				GeoPoint from = previous;
				Candidate next = remaining.stream()
					.min(Comparator.comparingLong(c -> from == null ? 0 : travel(from, c.location())))
					.orElseThrow();
				remaining.remove(next);
				route.add(next);
				previous = next.location();
			}
			route = withFixedSlots(route);
			long bestCost = cost(route);
			boolean improved = true;
			while (improved) {
				improved = false;
				for (int i = 0; i < route.size() - 1; i++) {
					for (int j = i + 1; j < route.size(); j++) {
						List<Candidate> candidate = new ArrayList<>(route);
						Collections.reverse(candidate.subList(i, j + 1));
						candidate = withFixedSlots(candidate);
						long cost = cost(candidate);
						if (cost < bestCost) {
							bestCost = cost;
							route = candidate;
							improved = true;
						}
					}
				}
			}
			return List.copyOf(route);
		}

		/** 순서 고정 항목을 받은 순서 자리로 옮긴다. */
		private List<Candidate> withFixedSlots(List<Candidate> route) {
			List<Candidate> loose = new ArrayList<>();
			Candidate[] slots = new Candidate[route.size()];
			for (Candidate candidate : route) {
				int index = candidate.orderFixed() ? Math.min(candidate.fixedOrder(), route.size()) - 1 : -1;
				if (index >= 0 && slots[index] == null) {
					slots[index] = candidate;
				} else {
					loose.add(candidate);
				}
			}
			int next = 0;
			for (int i = 0; i < slots.length; i++) {
				if (slots[i] == null) {
					slots[i] = loose.get(next++);
				}
			}
			return List.of(slots);
		}

		/** 이동 합계 + 식사 벌점 + 종료 시각 넘김 벌점. */
		long cost(List<Candidate> order) {
			Timeline timeline = timeline(order);
			boolean[] foods = new boolean[order.size()];
			for (int i = 0; i < foods.length; i++) {
				foods[i] = order.get(i).food();
			}
			return score(timeline.arrivals, foods, timeline.travel, timeline.finish);
		}

		/** 방문 순서대로의 도착 시각(분)·맛집 여부로 비용을 낸다. 모든 순서 비교와 근사 탐색이 같이 쓴다. */
		long score(long[] arrivals, boolean[] foods, long travel, long finish) {
			long cost = travel;
			int foodCount = 0;
			for (boolean food : foods) {
				foodCount += food ? 1 : 0;
			}
			boolean needsLunch = foodCount >= 1 && overlaps(day, LUNCH_FROM, LUNCH_TO);
			boolean needsDinner = overlaps(day, DINNER_FROM, DINNER_TO) && foodCount >= (needsLunch ? 2 : 1);
			long lunch = -1;
			int lunchIndex = -1;
			if (needsLunch) {
				for (int i = 0; i < foods.length; i++) {
					if (foods[i] && within(arrivals[i], LUNCH_FROM, LUNCH_TO)) {
						lunch = arrivals[i];
						lunchIndex = i;
						break;
					}
				}
				if (lunchIndex < 0) {
					cost += LUNCH_MISSED;
				}
			}
			if (needsDinner) {
				boolean dinner = false;
				for (int i = 0; i < foods.length; i++) {
					if (foods[i] && i != lunchIndex && within(arrivals[i], DINNER_FROM, DINNER_TO)
						&& (lunchIndex < 0 || arrivals[i] >= lunch + MEAL_GAP.toMinutes())) {
						dinner = true;
						break;
					}
				}
				if (!dinner) {
					cost += DINNER_MISSED;
				}
			}
			return cost + OVERFLOW_PER_MINUTE * Math.max(0, finish - endMinute);
		}

		long overflow(List<Candidate> order) {
			return order.isEmpty() ? 0 : Math.max(0, timeline(order).finish - endMinute);
		}

		/** 고정하지 않은 항목 중 빼면 가장 많은 시간이 남는 것. 맛집은 맛집이 아닌 것이 없을 때만 뺀다. */
		private Candidate dropCandidate(List<Candidate> order) {
			Candidate best = null;
			long bestSaved = -1;
			boolean bestFood = true;
			for (int i = 0; i < order.size(); i++) {
				Candidate candidate = order.get(i);
				if (candidate.orderFixed()) {
					continue;
				}
				GeoPoint previous = i == 0 ? start : order.get(i - 1).location();
				GeoPoint next = i == order.size() - 1 ? end : order.get(i + 1).location();
				long saved = candidate.stayMinutes() + travel(previous, candidate.location())
					+ travel(candidate.location(), next) - travel(previous, next);
				boolean better = best == null || (bestFood && !candidate.food())
					|| (bestFood == candidate.food() && saved > bestSaved);
				if (better) {
					best = candidate;
					bestSaved = saved;
					bestFood = candidate.food();
				}
			}
			return best;
		}

		private boolean allKnown(List<Candidate> order) {
			GeoPoint previous = start;
			for (Candidate candidate : order) {
				if (previous != null && !isKnown(previous, candidate.location())) {
					return false;
				}
				previous = candidate.location();
			}
			return end == null || previous == null || isKnown(previous, end);
		}

		private Timeline timeline(List<Candidate> order) {
			long[] arrivals = new long[order.size()];
			long clock = startMinute;
			long travel = 0;
			GeoPoint previous = start;
			for (int i = 0; i < order.size(); i++) {
				Candidate candidate = order.get(i);
				long leg = travel(previous, candidate.location());
				travel += leg;
				clock += leg;
				arrivals[i] = clock;
				clock += candidate.stayMinutes();
				previous = candidate.location();
			}
			long tail = travel(previous, end);
			return new Timeline(arrivals, travel + tail, clock + tail);
		}

		/** 분. 지점이 없으면 0. 같은 좌표면 0. 모르면 직선 거리 어림값. */
		long travel(GeoPoint from, GeoPoint to) {
			if (from == null || to == null || sameLocation(from, to)) {
				return 0;
			}
			Pair key = key(from, to);
			Integer cached = minutes.get(key);
			if (cached != null) {
				return cached;
			}
			TravelLeg leg = provider.estimate(from, to, mode);
			int value = leg.isKnown() ? leg.travelMinutes() : estimateMinutes(from, to, mode);
			minutes.put(key, value);
			known.put(key, leg.isKnown());
			return value;
		}

		private boolean isKnown(GeoPoint from, GeoPoint to) {
			if (sameLocation(from, to)) {
				return true;
			}
			travel(from, to);
			return known.get(key(from, to));
		}

		private Pair key(GeoPoint from, GeoPoint to) {
			return new Pair(from, to);
		}
	}

	private record Pair(GeoPoint from, GeoPoint to) {
	}

	private record Timeline(long[] arrivals, long travel, long finish) {
	}

	/* ── 공통 계산 ─────────────────────────────────────────── */

	private static int cost(Candidate candidate) {
		return candidate.stayMinutes() + ASSUMED_TRAVEL_MINUTES;
	}

	private static long capacity(DayOption day) {
		return Duration.between(day.startTime(), day.endTime()).toMinutes();
	}

	private static boolean overlaps(DayOption day, LocalTime from, LocalTime to) {
		return day.startTime().isBefore(to) && day.endTime().isAfter(from);
	}

	private static boolean within(long minuteOfDay, LocalTime from, LocalTime to) {
		return minuteOfDay >= from.toSecondOfDay() / 60 && minuteOfDay <= to.toSecondOfDay() / 60;
	}

	/** 이동 시간을 모를 때 순서·배정을 고르는 데만 쓰는 어림값. 직선 거리에 우회 1.3 배, 수단별 평균 속도. */
	static int estimateMinutes(GeoPoint from, GeoPoint to, TransportMode mode) {
		double kmPerHour = mode == TransportMode.CAR ? 40 : 20;
		return (int)Math.ceil(distanceKm(from, point(to)) * 1.3 / kmPerHour * 60);
	}

	private static boolean sameLocation(GeoPoint a, GeoPoint b) {
		return a.latitude().compareTo(b.latitude()) == 0 && a.longitude().compareTo(b.longitude()) == 0;
	}

	private static double[] point(GeoPoint point) {
		return new double[] {point.latitude().doubleValue(), point.longitude().doubleValue()};
	}

	private static double[] centroid(List<Candidate> list) {
		double lat = 0;
		double lng = 0;
		for (Candidate candidate : list) {
			lat += candidate.location().latitude().doubleValue();
			lng += candidate.location().longitude().doubleValue();
		}
		return new double[] {lat / list.size(), lng / list.size()};
	}

	private static boolean sameCenters(Map<LocalDate, double[]> a, Map<LocalDate, double[]> b) {
		for (Map.Entry<LocalDate, double[]> entry : a.entrySet()) {
			double[] other = b.get(entry.getKey());
			if (other == null || Math.abs(other[0] - entry.getValue()[0]) > 1e-9
				|| Math.abs(other[1] - entry.getValue()[1]) > 1e-9) {
				return false;
			}
		}
		return true;
	}

	private static double nearestDistance(GeoPoint location, Map<LocalDate, double[]> centers) {
		return centers.values().stream().mapToDouble(center -> distanceKm(location, center)).min().orElse(0);
	}

	private static double distanceKm(GeoPoint from, GeoPoint to) {
		return distanceKm(from, point(to));
	}

	/** 두 좌표 사이 직선(대원) 거리(km). */
	static double distanceKm(GeoPoint from, double[] to) {
		double lat1 = Math.toRadians(from.latitude().doubleValue());
		double lat2 = Math.toRadians(to[0]);
		double dLat = lat2 - lat1;
		double dLng = Math.toRadians(to[1] - from.longitude().doubleValue());
		double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
			+ Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
	}
}
