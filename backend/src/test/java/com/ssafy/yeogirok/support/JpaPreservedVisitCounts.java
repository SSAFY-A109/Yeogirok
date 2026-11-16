package com.ssafy.yeogirok.support;

import java.time.Instant;
import java.util.List;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.statistics.PreservedVisit;
import com.ssafy.yeogirok.domain.statistics.PreservedVisitCounts;
import com.ssafy.yeogirok.domain.statistics.entity.FootprintStatistic;
import com.ssafy.yeogirok.domain.statistics.entity.FootprintStatisticId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 테스트(H2)용 탈퇴자 보존분 저장. H2 에는 {@code ON CONFLICT DO UPDATE} 가 없어 JPA 로 읽고 더한다. 동시 탈퇴는 고려하지
 * 않는다(운영 SQL 은 {@code PostgresPreservedVisitCountsTest} 가 실제 PostgreSQL 에서 본다).
 */
public class JpaPreservedVisitCounts implements PreservedVisitCounts {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void add(List<PreservedVisit> visits, Instant now) {
		for (PreservedVisit visit : visits) {
			FootprintStatisticId id = new FootprintStatisticId(visit.aggregationVersion(), visit.cellId(),
				visit.statDate(), visit.categoryCodes());
			FootprintStatistic existing = entityManager.find(FootprintStatistic.class, id);
			long total = visit.visitCount() + (existing == null ? 0 : existing.getVisitCount());
			entityManager.merge(new FootprintStatistic(id, visit.centerLatitude(), visit.centerLongitude(), total));
		}
	}
}
