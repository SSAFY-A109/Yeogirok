package com.ssafy.yeogirok.domain.statistics;

import java.time.Instant;
import java.util.List;

/**
 * footprint_statistics 에 탈퇴자 보존분을 더한다. 같은 키(aggregation_version, cell_id, stat_date, category_codes)가
 * 이미 있으면 visit_count 에 더하고, 없으면 새로 만든다. 호출한 트랜잭션 안에서 쓴다.
 *
 * <p>운영(PostgreSQL)은 {@link PostgresPreservedVisitCounts}. 테스트(H2)는 {@code ON CONFLICT DO UPDATE} 가 없어
 * {@code JpaPreservedVisitCounts} 로 바꿔 쓴다.
 */
public interface PreservedVisitCounts {

	/**
	 * @param visits 키 순서로 정렬해 넘긴다. 동시에 탈퇴한 두 회원이 같은 행들을 서로 다른 순서로 잠가 교착되지 않게 한다
	 */
	void add(List<PreservedVisit> visits, Instant now);
}
