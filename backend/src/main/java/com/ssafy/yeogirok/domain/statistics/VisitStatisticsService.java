package com.ssafy.yeogirok.domain.statistics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.BoundingBox;

import lombok.RequiredArgsConstructor;

/**
 * API-046 카테고리별 방문 분포. 조회할 때마다 미삭제 발자취를 셀·카테고리 집합별로 센 값에
 * footprint_statistics(탈퇴자 보존분)를 더한다(노션 API-046). 삭제한 발자취는 바로 빠진다.
 *
 * <p>category 를 주면 그 코드를 고른 발자취만 세고, categoryCounts 에는 그 발자취들이 함께 고른 코드도 센다(11/4 결정).
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(VisitStatisticsProperties.class)
public class VisitStatisticsService {

	public static final String METRIC_VISIT_COUNT = "VISIT_COUNT";
	static final String UNIT = "LAT_LNG_GRID";

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Pattern CATEGORY_CODE = Pattern.compile("\"([A-Z_]+)\"");

	private final VisitStatisticsProperties properties;
	private final VisitStatisticsQuery query;

	@Transactional(readOnly = true)
	public VisitStatisticsResponse getStatistics(String bbox, double zoom, String category, LocalDate from,
		LocalDate to, String metric) {
		if (metric != null && !METRIC_VISIT_COUNT.equals(metric)) {
			throw new BusinessException(ExceptionType.UNSUPPORTED_METRIC, "metric: VISIT_COUNT 만 지원합니다.");
		}
		BoundingBox box = BoundingBox.parseOrNull(bbox);
		if (box == null) {
			throw new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING, "bbox 파라미터가 필요합니다.");
		}
		VisitGrid grid = gridFor(zoom);
		if (category != null && TravelCategory.fromCode(category).isEmpty()) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "category: 없는 카테고리입니다: " + category);
		}
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "from 이 to 보다 늦습니다.");
		}

		BoundingBox area = grid.expandToCells(box);
		Instant visitedFrom = from == null ? null : from.atStartOfDay(SEOUL).toInstant();
		Instant visitedTo = to == null ? null : to.plusDays(1).atStartOfDay(SEOUL).toInstant();

		Map<CellKey, Tally> tallies = new HashMap<>();
		List<VisitStatisticsQuery.Row> rows = new ArrayList<>(
			query.countFootprints(grid, area, visitedFrom, visitedTo, category));
		rows.addAll(query.countPreserved(grid, properties.aggregationVersion(), area, from, to, category));
		for (VisitStatisticsQuery.Row row : rows) {
			tallies.computeIfAbsent(new CellKey(row.latitudeIndex(), row.longitudeIndex()), key -> new Tally())
				.add(row.codes(), row.count());
		}

		List<VisitStatisticsResponse.Cell> cells = tallies.entrySet().stream()
			.filter(entry -> entry.getValue().count >= properties.minCount())
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> toCell(grid, entry.getKey(), entry.getValue()))
			.toList();

		return new VisitStatisticsResponse(
			METRIC_VISIT_COUNT,
			new VisitStatisticsResponse.Period(from, to),
			new VisitStatisticsResponse.Aggregation(UNIT, properties.aggregationVersion(), grid.precision(),
				properties.minCount()),
			cells,
			Instant.now());
	}

	private VisitGrid gridFor(double zoom) {
		if (Double.isNaN(zoom) || zoom < 0) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "zoom: 0 이상이어야 합니다.");
		}
		int multiplier = properties.multiplierFor(zoom)
			.orElseThrow(() -> new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "zoom: 지원하지 않는 zoom 입니다."));
		return new VisitGrid(properties.baseCellSizeDegrees(), multiplier);
	}

	private static VisitStatisticsResponse.Cell toCell(VisitGrid grid, CellKey key, Tally tally) {
		return new VisitStatisticsResponse.Cell(
			grid.cellId(key.latitudeIndex, key.longitudeIndex),
			new VisitStatisticsResponse.Center(grid.center(key.latitudeIndex), grid.center(key.longitudeIndex)),
			tally.count,
			tally.categoryCounts());
	}

	/** 북쪽(위도 칸 큰 쪽)부터, 같은 줄은 서쪽부터. */
	private record CellKey(long latitudeIndex, long longitudeIndex) implements Comparable<CellKey> {

		private static final Comparator<CellKey> ORDER = Comparator.comparingLong(CellKey::latitudeIndex).reversed()
			.thenComparingLong(CellKey::longitudeIndex);

		@Override
		public int compareTo(CellKey other) {
			return ORDER.compare(this, other);
		}
	}

	/** 한 셀의 전체 횟수와 카테고리별 횟수. 여러 개 고른 발자취는 전체에 1, 각 코드에 1씩 더한다. */
	private static final class Tally {

		private long count;
		private final Map<TravelCategory, Long> byCategory = new EnumMap<>(TravelCategory.class);

		void add(String codes, long times) {
			count += times;
			Matcher matcher = CATEGORY_CODE.matcher(codes);
			while (matcher.find()) {
				TravelCategory.fromCode(matcher.group(1))
					.ifPresent(category -> byCategory.merge(category, times, Long::sum));
			}
		}

		/** 공통 카테고리 순서로, 0 인 코드는 없이. */
		Map<String, Long> categoryCounts() {
			Map<String, Long> counts = new LinkedHashMap<>();
			byCategory.forEach((category, value) -> counts.put(category.name(), value));
			return counts;
		}
	}
}
