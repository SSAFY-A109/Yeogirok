package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.geo.BoundingBox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * API-046 집계 조회. 셀·카테고리 집합별 횟수를 DB 에서 센다. 카테고리별로 펼치는 일은 서비스가 한다.
 *
 * <p>JSON 배열은 문자열로 CAST 해서 묶고 비교한다(PostgreSQL·H2 공통). 셀 계산식을 GROUP BY 에 다시 쓰면
 * PostgreSQL 이 매개변수를 다른 식으로 보므로 안쪽 쿼리에서 계산하고 바깥에서 묶는다.
 */
@Component
class VisitStatisticsQuery {

	@PersistenceContext
	private EntityManager entityManager;

	/** 삭제하지 않은 발자취. 칸 번호는 이 격자 기준. */
	List<Row> countFootprints(VisitGrid grid, BoundingBox area, Instant visitedFrom, Instant visitedTo,
		String category) {
		StringBuilder where = new StringBuilder("""
			deleted_at IS NULL
			AND latitude >= :south AND latitude < :north AND longitude >= :west AND longitude < :east
			""");
		if (visitedFrom != null) {
			where.append(" AND visited_at >= :visitedFrom");
		}
		if (visitedTo != null) {
			where.append(" AND visited_at < :visitedTo");
		}
		if (category != null) {
			where.append(" AND CAST(category_codes AS VARCHAR) LIKE :categoryPattern");
		}
		Query query = entityManager.createNativeQuery("""
			SELECT lat_index, lng_index, codes, COUNT(*)
			FROM (
			    SELECT FLOOR(latitude / :cellSize) AS lat_index, FLOOR(longitude / :cellSize) AS lng_index,
			           CAST(category_codes AS VARCHAR) AS codes
			    FROM footprints
			    WHERE %s
			) cells
			GROUP BY lat_index, lng_index, codes
			""".formatted(where));
		query.setParameter("cellSize", grid.cellSize());
		bindArea(query, area);
		if (visitedFrom != null) {
			query.setParameter("visitedFrom", visitedFrom);
		}
		if (visitedTo != null) {
			query.setParameter("visitedTo", visitedTo);
		}
		if (category != null) {
			query.setParameter("categoryPattern", categoryPattern(category));
		}
		return toRows(query, grid, false);
	}

	/** 탈퇴자 보존분. cell_id 는 기본 셀 "{위도 칸}:{경도 칸}" 이고 이 격자 칸 번호로 바꿔 돌려준다. */
	List<Row> countPreserved(VisitGrid grid, String aggregationVersion, BoundingBox area, LocalDate from,
		LocalDate to, String category) {
		StringBuilder where = new StringBuilder("""
			aggregation_version = :version
			AND center_latitude >= :south AND center_latitude < :north
			AND center_longitude >= :west AND center_longitude < :east
			""");
		if (from != null) {
			where.append(" AND stat_date >= :fromDate");
		}
		if (to != null) {
			where.append(" AND stat_date <= :toDate");
		}
		if (category != null) {
			where.append(" AND CAST(category_codes AS VARCHAR) LIKE :categoryPattern");
		}
		Query query = entityManager.createNativeQuery("""
			SELECT cell_id, codes, SUM(visit_count)
			FROM (
			    SELECT cell_id, CAST(category_codes AS VARCHAR) AS codes, visit_count
			    FROM footprint_statistics
			    WHERE %s
			) preserved
			GROUP BY cell_id, codes
			""".formatted(where));
		query.setParameter("version", aggregationVersion);
		bindArea(query, area);
		if (from != null) {
			query.setParameter("fromDate", from);
		}
		if (to != null) {
			query.setParameter("toDate", to);
		}
		if (category != null) {
			query.setParameter("categoryPattern", categoryPattern(category));
		}
		return toRows(query, grid, true);
	}

	private static void bindArea(Query query, BoundingBox area) {
		query.setParameter("south", area.south());
		query.setParameter("north", area.north());
		query.setParameter("west", area.west());
		query.setParameter("east", area.east());
	}

	private static String categoryPattern(String category) {
		return "%\"" + category + "\"%";
	}

	@SuppressWarnings("unchecked")
	private static List<Row> toRows(Query query, VisitGrid grid, boolean baseCellId) {
		List<Row> rows = new ArrayList<>();
		for (Object[] result : (List<Object[]>)query.getResultList()) {
			long latitudeIndex;
			long longitudeIndex;
			String codes;
			long count;
			if (baseCellId) {
				String[] cell = ((String)result[0]).split(":");
				latitudeIndex = grid.toCellIndex(Long.parseLong(cell[0]));
				longitudeIndex = grid.toCellIndex(Long.parseLong(cell[1]));
			} else {
				latitudeIndex = toLong(result[0]);
				longitudeIndex = toLong(result[1]);
			}
			codes = (String)result[baseCellId ? 1 : 2];
			count = toLong(result[baseCellId ? 2 : 3]);
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
