package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.BoundingBox;

import lombok.RequiredArgsConstructor;

/**
 * API-046. 조회할 때 미삭제 발자취를 세고 footprint_statistics(탈퇴자 보존분)를 더한다.
 * category 를 주면 그 코드를 고른 발자취만 세고, categoryCounts 에는 함께 고른 코드도 센다.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(VisitStatisticsProperties.class)
public class VisitStatisticsService {

	public static final String METRIC_VISIT_COUNT = "VISIT_COUNT";
	static final String UNIT = "LAT_LNG_GRID";

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Pattern CATEGORY_CODE = Pattern.compile("\"([A-Z_]+)\"");
	/** 서비스 지역(한국). 이 밖은 조회하지 않는다. */
	static final BoundingBox KOREA = new BoundingBox(
		BigDecimal.valueOf(124), BigDecimal.valueOf(33), BigDecimal.valueOf(132), BigDecimal.valueOf(39));

	private final VisitStatisticsProperties properties;
	private final VisitStatisticsQuery query;

	/** 발자취와 보존분을 같은 시점으로 읽는다. 두 쿼리 사이에 탈퇴 처리(발자취 → 보존분 이동)가 끼면 이중 집계되기 때문이다. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public VisitStatisticsResponse getStatistics(String bbox, double zoom, String category, LocalDate from,
		LocalDate to, String metric) {
		if (metric != null && !METRIC_VISIT_COUNT.equals(metric)) {
			throw new BusinessException(ExceptionType.UNSUPPORTED_METRIC, "metric: VISIT_COUNT 만 지원합니다.");
		}
		BoundingBox box = BoundingBox.parseOrNull(bbox);
		VisitGrid grid = gridFor(zoom);
		if (category != null && TravelCategory.fromCode(category).isEmpty()) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "category: 없는 카테고리입니다: " + category);
		}
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "from 이 to 보다 늦습니다.");
		}

		BoundingBox korea = intersect(box, KOREA);
		if (korea == null) {
			return response(from, to, grid, List.of());
		}
		BoundingBox area = grid.expandToCells(korea);
		rejectIfTooManyCells(area, grid);
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

		return response(from, to, grid, cells);
	}

	private VisitStatisticsResponse response(LocalDate from, LocalDate to, VisitGrid grid,
		List<VisitStatisticsResponse.Cell> cells) {
		return new VisitStatisticsResponse(
			METRIC_VISIT_COUNT,
			new VisitStatisticsResponse.Period(from, to),
			new VisitStatisticsResponse.Aggregation(UNIT, properties.aggregationVersion(), grid.precision(),
				properties.minCount()),
			cells,
			Instant.now());
	}

	/** 두 영역이 겹치는 부분. 넓이가 있는 겹침이 없으면 null. */
	private static BoundingBox intersect(BoundingBox a, BoundingBox b) {
		BigDecimal west = a.west().max(b.west());
		BigDecimal south = a.south().max(b.south());
		BigDecimal east = a.east().min(b.east());
		BigDecimal north = a.north().min(b.north());
		return west.compareTo(east) < 0 && south.compareTo(north) < 0 ? new BoundingBox(west, south, east, north) : null;
	}

	/** 셀 경계로 넓힌 영역의 셀 수가 max-cells 를 넘으면 C013. */
	private void rejectIfTooManyCells(BoundingBox area, VisitGrid grid) {
		BigDecimal size = grid.cellSize();
		BigDecimal cells = area.east().subtract(area.west()).divide(size, 0, RoundingMode.CEILING)
			.multiply(area.north().subtract(area.south()).divide(size, 0, RoundingMode.CEILING));
		if (cells.compareTo(BigDecimal.valueOf(properties.maxCells())) > 0) {
			throw new BusinessException(ExceptionType.INVALID_BBOX,
				"bbox: 조회 영역이 너무 넓습니다. zoom 을 낮추거나 영역을 줄여 주세요.");
		}
	}

	private VisitGrid gridFor(double zoom) {
		if (Double.isNaN(zoom) || zoom < 0) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "zoom: 0 이상이어야 합니다.");
		}
		return new VisitGrid(properties.baseCellSizeDegrees(), properties.zoomMultipliers().multiplierFor(zoom));
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
