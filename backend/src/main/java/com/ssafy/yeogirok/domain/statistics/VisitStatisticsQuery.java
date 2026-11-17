package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.footprint.entity.FootprintCategories;
import com.ssafy.yeogirok.global.geo.BoundingBox;
import com.ssafy.yeogirok.global.request.DateRange;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * API-046 집계 조회. 셀·카테고리 집합별 횟수를 센다. 셀 계산식은 안쪽 쿼리에서 계산한다
 * (GROUP BY 에 다시 쓰면 PostgreSQL 이 매개변수를 다른 식으로 본다).
 */
@Component
class VisitStatisticsQuery {

	@PersistenceContext
	private EntityManager entityManager;

	/** 삭제하지 않은 발자취. 칸 번호는 이 격자 기준. officialUserId 의 발자취는 실제 사용자 수에서 뺀다(공식 계정이 없으면 -1). */
	List<Row> countFootprints(VisitGrid grid, BoundingBox area, DateRange dates, String category, long officialUserId) {
		Where where = footprintWhere(area, dates, category);
		Query query = where.bind(entityManager.createNativeQuery("""
			SELECT lat_index, lng_index, codes, COUNT(*), SUM(latitude), SUM(longitude),
			       SUM(CASE WHEN user_id = :officialUserId THEN 0 ELSE 1 END)
			FROM (
			    SELECT FLOOR(latitude / :cellSize) AS lat_index, FLOOR(longitude / :cellSize) AS lng_index,
			           CAST(category_codes AS VARCHAR) AS codes, latitude, longitude, user_id
			    FROM footprints
			    WHERE %s
			) cells
			GROUP BY lat_index, lng_index, codes
			""".formatted(where.sql())));
		query.setParameter("cellSize", grid.cellSize());
		query.setParameter("officialUserId", officialUserId);
		return toRows(query, grid, false);
	}

	/**
	 * 전국 보기(도 9곳). 법정동 코드가 있는 발자취는 코드 앞 2자리·카테고리 집합별로 센다. 코드가 없는 발자취는
	 * fallbackCellSize 칸별로 따로 세어, 서비스가 칸의 평균 위치로 가까운 도를 고르게 한다(좌표 합은 그때만 쓴다).
	 * 실제 사용자 수는 세지 않는다(도는 정해 둔 위치를 주므로 쓰지 않는다).
	 */
	@SuppressWarnings("unchecked")
	List<ProvinceRow> countFootprintsByProvince(BoundingBox area, DateRange dates, String category,
		BigDecimal fallbackCellSize) {
		Where where = footprintWhere(area, dates, category);
		Query query = where.bind(entityManager.createNativeQuery("""
			SELECT prefix, lat_index, lng_index, codes, COUNT(*), SUM(latitude), SUM(longitude)
			FROM (
			    SELECT SUBSTRING(legal_area_code, 1, 2) AS prefix,
			           CASE WHEN legal_area_code IS NULL THEN FLOOR(latitude / :cellSize) ELSE 0 END AS lat_index,
			           CASE WHEN legal_area_code IS NULL THEN FLOOR(longitude / :cellSize) ELSE 0 END AS lng_index,
			           CAST(category_codes AS VARCHAR) AS codes, latitude, longitude
			    FROM footprints
			    WHERE %s
			) provinces
			GROUP BY prefix, lat_index, lng_index, codes
			""".formatted(where.sql())));
		query.setParameter("cellSize", fallbackCellSize);
		List<ProvinceRow> rows = new ArrayList<>();
		for (Object[] result : (List<Object[]>)query.getResultList()) {
			rows.add(new ProvinceRow((String)result[0], new Row(toLong(result[1]), toLong(result[2]), (String)result[3],
				toLong(result[4]), toDecimal(result[5]), toDecimal(result[6]), 0)));
		}
		return rows;
	}

	/** 삭제하지 않은 발자취 중 영역·방문 날짜·카테고리에 맞는 것. */
	private static Where footprintWhere(BoundingBox area, DateRange dates, String category) {
		return new Where("deleted_at IS NULL", Map.of())
			.and("latitude >= :south AND latitude < :north AND longitude >= :west AND longitude < :east", area(area))
			.andIf(dates.startInclusive() != null, "visited_at >= :visitedFrom", "visitedFrom", dates.startInclusive())
			.andIf(dates.endExclusive() != null, "visited_at < :visitedTo", "visitedTo", dates.endExclusive())
			.andIf(category != null, "CAST(category_codes AS VARCHAR) LIKE :categoryPattern",
				"categoryPattern", category == null ? null : FootprintCategories.containsPattern(category));
	}

	/**
	 * 탈퇴자 보존분. cell_id 는 기본 셀 "{위도 칸}:{경도 칸}" 이고 이 격자 칸 번호로 바꿔 돌려준다.
	 * 누구 것인지 모르므로 모두 실제 사용자로 세고, 위치는 기본 셀 중심을 횟수만큼 더한다.
	 */
	List<Row> countPreserved(VisitGrid grid, String aggregationVersion, BoundingBox area, DateRange dates,
		String category) {
		Where where = new Where("aggregation_version = :version", Map.of("version", aggregationVersion))
			.and("center_latitude >= :south AND center_latitude < :north"
				+ " AND center_longitude >= :west AND center_longitude < :east", area(area))
			.andIf(dates.from() != null, "stat_date >= :fromDate", "fromDate", dates.from())
			.andIf(dates.to() != null, "stat_date <= :toDate", "toDate", dates.to())
			.andIf(category != null, "CAST(category_codes AS VARCHAR) LIKE :categoryPattern",
				"categoryPattern", category == null ? null : FootprintCategories.containsPattern(category));
		Query query = where.bind(entityManager.createNativeQuery("""
			SELECT cell_id, codes, SUM(visit_count), SUM(center_latitude * visit_count),
			       SUM(center_longitude * visit_count), SUM(visit_count)
			FROM (
			    SELECT cell_id, CAST(category_codes AS VARCHAR) AS codes, visit_count, center_latitude, center_longitude
			    FROM footprint_statistics
			    WHERE %s
			) preserved
			GROUP BY cell_id, codes
			""".formatted(where.sql())));
		return toRows(query, grid, true);
	}

	private static Map<String, Object> area(BoundingBox area) {
		return Map.of("south", area.south(), "north", area.north(), "west", area.west(), "east", area.east());
	}

	/** WHERE 조건과 매개변수를 함께 모은다. 조건을 붙이는 곳과 값을 넣는 곳이 어긋나 "parameter not bound" 가 나지 않게 한다. */
	private static final class Where {

		private final StringBuilder sql;
		private final Map<String, Object> parameters = new LinkedHashMap<>();

		Where(String condition, Map<String, Object> values) {
			this.sql = new StringBuilder(condition);
			this.parameters.putAll(values);
		}

		Where and(String condition, Map<String, Object> values) {
			sql.append(" AND ").append(condition);
			parameters.putAll(values);
			return this;
		}

		Where andIf(boolean apply, String condition, String name, Object value) {
			if (apply) {
				sql.append(" AND ").append(condition);
				parameters.put(name, value);
			}
			return this;
		}

		String sql() {
			return sql.toString();
		}

		Query bind(Query query) {
			parameters.forEach(query::setParameter);
			return query;
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Row> toRows(Query query, VisitGrid grid, boolean baseCellId) {
		List<Row> rows = new ArrayList<>();
		for (Object[] result : (List<Object[]>)query.getResultList()) {
			long latitudeIndex;
			long longitudeIndex;
			if (baseCellId) {
				String[] cell = ((String)result[0]).split(":");
				latitudeIndex = grid.toCellIndex(Long.parseLong(cell[0]));
				longitudeIndex = grid.toCellIndex(Long.parseLong(cell[1]));
			} else {
				latitudeIndex = toLong(result[0]);
				longitudeIndex = toLong(result[1]);
			}
			int first = baseCellId ? 1 : 2;
			rows.add(new Row(latitudeIndex, longitudeIndex, (String)result[first], toLong(result[first + 1]),
				toDecimal(result[first + 2]), toDecimal(result[first + 3]), toLong(result[first + 4])));
		}
		return rows;
	}

	private static BigDecimal toDecimal(Object value) {
		return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
	}

	private static long toLong(Object value) {
		return value instanceof BigDecimal decimal ? decimal.longValueExact() : ((Number)value).longValue();
	}

	/** 한 셀·한 카테고리 집합의 횟수·좌표 합과 그중 실제 사용자(공식 계정 아님) 횟수. codes 는 JSON 배열 문자열. */
	record Row(long latitudeIndex, long longitudeIndex, String codes, long count, BigDecimal latitudeSum,
		BigDecimal longitudeSum, long userCount) {
	}

	/** 전국 보기 한 행. legalAreaPrefix 는 법정동 코드 앞 2자리이고, 코드가 없는 발자취면 null(row 의 칸 번호가 그 칸). */
	record ProvinceRow(String legalAreaPrefix, Row row) {
	}
}
