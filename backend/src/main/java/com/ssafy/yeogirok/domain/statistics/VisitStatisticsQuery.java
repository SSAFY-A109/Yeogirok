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

	/** 삭제하지 않은 발자취. 칸 번호는 이 격자 기준. */
	List<Row> countFootprints(VisitGrid grid, BoundingBox area, DateRange dates, String category) {
		Where where = new Where("deleted_at IS NULL", Map.of())
			.and("latitude >= :south AND latitude < :north AND longitude >= :west AND longitude < :east", area(area))
			.andIf(dates.startInclusive() != null, "visited_at >= :visitedFrom", "visitedFrom", dates.startInclusive())
			.andIf(dates.endExclusive() != null, "visited_at < :visitedTo", "visitedTo", dates.endExclusive())
			.andIf(category != null, "CAST(category_codes AS VARCHAR) LIKE :categoryPattern",
				"categoryPattern", category == null ? null : FootprintCategories.containsPattern(category));
		Query query = where.bind(entityManager.createNativeQuery("""
			SELECT lat_index, lng_index, codes, COUNT(*)
			FROM (
			    SELECT FLOOR(latitude / :cellSize) AS lat_index, FLOOR(longitude / :cellSize) AS lng_index,
			           CAST(category_codes AS VARCHAR) AS codes
			    FROM footprints
			    WHERE %s
			) cells
			GROUP BY lat_index, lng_index, codes
			""".formatted(where.sql())));
		query.setParameter("cellSize", grid.cellSize());
		return toRows(query, grid, false);
	}

	/** 탈퇴자 보존분. cell_id 는 기본 셀 "{위도 칸}:{경도 칸}" 이고 이 격자 칸 번호로 바꿔 돌려준다. */
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
			SELECT cell_id, codes, SUM(visit_count)
			FROM (
			    SELECT cell_id, CAST(category_codes AS VARCHAR) AS codes, visit_count
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
			String codes = (String)result[baseCellId ? 1 : 2];
			long count = toLong(result[baseCellId ? 2 : 3]);
			rows.add(new Row(latitudeIndex, longitudeIndex, codes, count));
		}
		return rows;
	}

	private static long toLong(Object value) {
		return value instanceof BigDecimal decimal ? decimal.longValueExact() : ((Number)value).longValue();
	}

	/** 한 셀·한 카테고리 집합의 횟수. codes 는 JSON 배열 문자열. */
	record Row(long latitudeIndex, long longitudeIndex, String codes, long count) {
	}
}
