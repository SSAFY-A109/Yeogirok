package com.ssafy.yeogirok.domain.statistics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * PostgreSQL {@code INSERT ... ON CONFLICT DO UPDATE}. 다른 회원의 탈퇴가 같은 키를 동시에 만들어도 한쪽이 기다렸다가
 * 더하므로 횟수가 사라지거나 유니크 위반으로 탈퇴가 실패하지 않는다. 테스트(H2)는 {@code JpaPreservedVisitCounts} 로 바꿔 쓴다.
 *
 * <p>회원 행을 잠근 채로 돌므로 왕복을 줄이려고 여러 행을 한 문장으로 넣는다. 한 문장의 행 수는 {@link #CHUNK_SIZES} 중
 * 하나다. 2의 거듭제곱이라 SQL 은 열 가지뿐이고(행 수마다 SQL 이 달라 쿼리 계획 캐시가 쌓이지 않게), 키가 1,023개 이하면
 * 문장도 열 개 이하다. 한 문장 안의 키는 서로 달라야 한다(같은 행을 두 번 고치면 PostgreSQL 이 거부한다). 집계가 이미 키별로
 * 묶어 넘긴다.
 */
@Component
public class PostgresPreservedVisitCounts implements PreservedVisitCounts {

	/**
	 * 한 문장의 행 수, 큰 것부터. 남은 행보다 크지 않은 가장 큰 단계로 넣는다(남은 수의 이진수 자리마다 한 문장).
	 * 512행 × 매개변수 8개는 PostgreSQL 한도(65,535) 안이다.
	 */
	static final List<Integer> CHUNK_SIZES = List.of(512, 256, 128, 64, 32, 16, 8, 4, 2, 1);
	private static final int PARAMETERS_PER_ROW = 8;
	/** 단계별 SQL. 열 가지뿐이라 시작할 때 한 번 만들어 둔다(문장마다 512행짜리 VALUES 를 다시 만들지 않게). */
	private static final Map<Integer, String> SQL_BY_ROWS = CHUNK_SIZES.stream()
		.collect(Collectors.toUnmodifiableMap(Function.identity(), PostgresPreservedVisitCounts::sql));

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void add(List<PreservedVisit> visits, Instant now) {
		int from = 0;
		while (from < visits.size()) {
			int remaining = visits.size() - from;
			int size = CHUNK_SIZES.stream().filter(chunkSize -> chunkSize <= remaining).findFirst().orElseThrow();
			List<PreservedVisit> chunk = visits.subList(from, from + size);
			from += size;
			Query query = entityManager.createNativeQuery(SQL_BY_ROWS.get(size));
			for (int row = 0; row < chunk.size(); row++) {
				PreservedVisit visit = chunk.get(row);
				int base = row * PARAMETERS_PER_ROW;
				query.setParameter(base + 1, visit.aggregationVersion())
					.setParameter(base + 2, visit.cellId())
					.setParameter(base + 3, visit.statDate())
					.setParameter(base + 4, visit.categoryCodes())
					.setParameter(base + 5, visit.centerLatitude())
					.setParameter(base + 6, visit.centerLongitude())
					.setParameter(base + 7, visit.visitCount())
					.setParameter(base + 8, now);
			}
			query.executeUpdate();
		}
	}

	/** category_codes 는 jsonb 라 문자열을 바꿔 넣는다. 같은 배열이면 jsonb 끼리 같은 값이라 키가 겹친다. */
	static String sql(int rows) {
		String values = IntStream.range(0, rows)
			.mapToObj(row -> {
				int base = row * PARAMETERS_PER_ROW;
				return "(?%d, ?%d, ?%d, CAST(?%d AS jsonb), ?%d, ?%d, ?%d, ?%d)".formatted(base + 1, base + 2,
					base + 3, base + 4, base + 5, base + 6, base + 7, base + 8);
			})
			.collect(Collectors.joining(",\n"));
		return """
			INSERT INTO footprint_statistics (aggregation_version, cell_id, stat_date, category_codes,
			    center_latitude, center_longitude, visit_count, updated_at)
			VALUES %s
			ON CONFLICT (aggregation_version, category_codes, cell_id, stat_date)
			DO UPDATE SET visit_count = footprint_statistics.visit_count + EXCLUDED.visit_count,
			    updated_at = EXCLUDED.updated_at
			""".formatted(values);
	}
}
