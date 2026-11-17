package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
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
import com.ssafy.yeogirok.domain.course.OfficialAccount;
import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.BoundingBox;
import com.ssafy.yeogirok.global.request.DateRange;

import lombok.RequiredArgsConstructor;

/**
 * API-046. 조회할 때 미삭제 발자취를 세고 footprint_statistics(탈퇴자 보존분)를 더한다.
 * category 를 주면 그 코드를 고른 발자취만 세고, categoryCounts 에는 함께 고른 코드도 센다.
 * center 는 셀 발자취의 평균 위치다. 단 실제 사용자 발자취가 1개 이상 center-min-count 미만이면 셀 중심을 준다
 * (공식 계정 발자취는 공개된 장소라 섞인 평균에서 한두 명의 위치를 역산할 수 있다. specs/visit-stats-center.md 3-1).
 * zoom 이 province-below-zoom 보다 작으면(전국 보기) 격자 대신 도 9곳({@link VisitProvince})으로 센다.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(VisitStatisticsProperties.class)
public class VisitStatisticsService {

	public static final String METRIC_VISIT_COUNT = "VISIT_COUNT";
	static final String UNIT = "LAT_LNG_GRID";
	static final String PROVINCE_UNIT = "PROVINCE";
	static final String PROVINCE_PRECISION = "province";
	/** 법정동 코드가 없는 발자취를 가까운 도로 넣을 때 묶는 칸(약 5km). 도 마커끼리 80km 넘게 떨어져 있어 이 정도면 충분하다. */
	private static final BigDecimal PROVINCE_FALLBACK_CELL_SIZE = new BigDecimal("0.05");
	private static final int COORDINATE_SCALE = 7;

	private static final Pattern CATEGORY_CODE = Pattern.compile("\"([A-Z_]+)\"");
	/** 서비스 지역(한국). 이 밖은 조회하지 않는다. */
	static final BoundingBox KOREA = new BoundingBox(
		BigDecimal.valueOf(124), BigDecimal.valueOf(33), BigDecimal.valueOf(132), BigDecimal.valueOf(39));

	private final VisitStatisticsProperties properties;
	private final VisitStatisticsQuery query;
	private final UserRepository userRepository;
	private final Clock clock;

	/** 발자취와 보존분을 같은 시점으로 읽는다. 두 쿼리 사이에 탈퇴 처리(발자취 → 보존분 이동)가 끼면 이중 집계되기 때문이다. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public VisitStatisticsResponse getStatistics(String bbox, double zoom, String category, LocalDate from,
		LocalDate to, String metric) {
		if (metric != null && !METRIC_VISIT_COUNT.equals(metric)) {
			throw new BusinessException(ExceptionType.UNSUPPORTED_METRIC, "metric: VISIT_COUNT 만 지원합니다.");
		}
		BoundingBox box = BoundingBox.parseOrNull(bbox);
		VisitGrid grid = gridFor(zoom);
		TravelCategory.validateFilter(category);
		DateRange dates = new DateRange(from, to);

		BoundingBox korea = intersect(box, KOREA);
		if (zoom < properties.provinceBelowZoom()) {
			return response(dates, PROVINCE_UNIT, PROVINCE_PRECISION,
				korea == null ? List.of() : countProvinces(dates, category));
		}
		if (korea == null) {
			return response(dates, grid, List.of());
		}
		BoundingBox area = grid.expandToCells(korea);
		rejectIfTooManyCells(area, grid);

		long officialUserId = userRepository.findByProviderAndProviderSubject(OfficialAccount.PROVIDER,
			OfficialAccount.SUBJECT).map(User::getId).orElse(-1L);
		Map<CellKey, Tally> tallies = new HashMap<>();
		List<VisitStatisticsQuery.Row> rows = new ArrayList<>(
			query.countFootprints(grid, area, dates, category, officialUserId));
		rows.addAll(query.countPreserved(grid, properties.aggregationVersion(), area, dates, category));
		for (VisitStatisticsQuery.Row row : rows) {
			tallies.computeIfAbsent(new CellKey(row.latitudeIndex(), row.longitudeIndex()), key -> new Tally())
				.add(row);
		}

		List<VisitStatisticsResponse.Cell> cells = tallies.entrySet().stream()
			.filter(entry -> entry.getValue().count >= properties.minCount())
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> toCell(grid, entry.getKey(), entry.getValue(), properties.centerMinCount()))
			.toList();

		return response(dates, grid, cells);
	}

	/**
	 * 전국 보기. bbox 와 상관없이 한국 전체를 도 9곳으로 센다(도 전체 수라 화면을 옮겨도 같은 도의 수는 그대로다).
	 * 법정동 코드가 있는 발자취는 코드로, 코드가 없는 발자취와 탈퇴자 보존분(기본 셀 중심)은 위치가 가까운 도로 넣는다.
	 */
	private List<VisitStatisticsResponse.Cell> countProvinces(DateRange dates, String category) {
		Map<VisitProvince, Tally> tallies = new EnumMap<>(VisitProvince.class);
		for (VisitStatisticsQuery.ProvinceRow row : query.countFootprintsByProvince(KOREA, dates, category,
			PROVINCE_FALLBACK_CELL_SIZE)) {
			VisitProvince province = VisitProvince.ofLegalAreaPrefix(row.legalAreaPrefix())
				.orElseGet(() -> nearestProvince(row.row()));
			tallies.computeIfAbsent(province, key -> new Tally()).add(row.row());
		}
		VisitGrid baseGrid = new VisitGrid(properties.baseCellSizeDegrees(), 1);
		for (VisitStatisticsQuery.Row row : query.countPreserved(baseGrid, properties.aggregationVersion(), KOREA,
			dates, category)) {
			tallies.computeIfAbsent(nearestProvince(row), key -> new Tally()).add(row);
		}

		return tallies.entrySet().stream()  // EnumMap 이라 도 순서다
			.filter(entry -> entry.getValue().count >= properties.minCount())
			.map(entry -> toProvinceCell(entry.getKey(), entry.getValue()))
			.toList();
	}

	/** 행의 평균 위치(보존분은 기본 셀 중심)에서 마커 위치가 가장 가까운 도. */
	private static VisitProvince nearestProvince(VisitStatisticsQuery.Row row) {
		BigDecimal count = BigDecimal.valueOf(row.count());
		return VisitProvince.nearest(row.latitudeSum().divide(count, COORDINATE_SCALE, RoundingMode.HALF_UP),
			row.longitudeSum().divide(count, COORDINATE_SCALE, RoundingMode.HALF_UP));
	}

	private VisitStatisticsResponse response(DateRange dates, VisitGrid grid, List<VisitStatisticsResponse.Cell> cells) {
		return response(dates, UNIT, grid.precision(), cells);
	}

	private VisitStatisticsResponse response(DateRange dates, String unit, String precision,
		List<VisitStatisticsResponse.Cell> cells) {
		return new VisitStatisticsResponse(
			METRIC_VISIT_COUNT,
			new VisitStatisticsResponse.Period(dates.from(), dates.to()),
			new VisitStatisticsResponse.Aggregation(unit, properties.aggregationVersion(), precision,
				properties.minCount()),
			cells,
			clock.instant());
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

	private static VisitStatisticsResponse.Cell toCell(VisitGrid grid, CellKey key, Tally tally, int centerMinCount) {
		boolean average = tally.userCount == 0 || tally.userCount >= centerMinCount;
		VisitStatisticsResponse.Center center = average
			? new VisitStatisticsResponse.Center(tally.average(tally.latitudeSum), tally.average(tally.longitudeSum))
			: new VisitStatisticsResponse.Center(grid.center(key.latitudeIndex), grid.center(key.longitudeIndex));
		return new VisitStatisticsResponse.Cell(grid.cellId(key.latitudeIndex, key.longitudeIndex), center, tally.count,
			tally.categoryCounts(), null);
	}

	/** 도 마커는 정해 둔 위치에 둔다. 발자취 평균을 쓰지 않아 센 수와 상관없이 한 사람의 위치가 드러나지 않는다. */
	private static VisitStatisticsResponse.Cell toProvinceCell(VisitProvince province, Tally tally) {
		return new VisitStatisticsResponse.Cell(province.cellId(),
			new VisitStatisticsResponse.Center(province.latitude(), province.longitude()), tally.count,
			tally.categoryCounts(), province.displayName());
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
		private long userCount;
		private BigDecimal latitudeSum = BigDecimal.ZERO;
		private BigDecimal longitudeSum = BigDecimal.ZERO;
		private final Map<TravelCategory, Long> byCategory = new EnumMap<>(TravelCategory.class);

		void add(VisitStatisticsQuery.Row row) {
			long times = row.count();
			count += times;
			userCount += row.userCount();
			latitudeSum = latitudeSum.add(row.latitudeSum());
			longitudeSum = longitudeSum.add(row.longitudeSum());
			Matcher matcher = CATEGORY_CODE.matcher(row.codes());
			while (matcher.find()) {
				TravelCategory.fromCode(matcher.group(1))
					.ifPresent(category -> byCategory.merge(category, times, Long::sum));
			}
		}

		/** 좌표 평균, 소수 7자리(셀 중심과 같은 반올림). 셀 안 점들의 평균이라 셀 안에 있다. */
		BigDecimal average(BigDecimal sum) {
			return sum.divide(BigDecimal.valueOf(count), COORDINATE_SCALE, RoundingMode.HALF_UP);
		}

		/** 공통 카테고리 순서로, 0 인 코드는 없이. */
		Map<String, Long> categoryCounts() {
			Map<String, Long> counts = new LinkedHashMap<>();
			byCategory.forEach((category, value) -> counts.put(category.name(), value));
			return counts;
		}
	}
}
