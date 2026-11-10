package com.ssafy.yeogirok.domain.record.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.record.entity.ActualVisit;

public interface ActualVisitRepository extends JpaRepository<ActualVisit, Long> {

	/** 날짜·날짜 안 순서대로. */
	List<ActualVisit> findByRecordIdOrderByVisitDateAscPositionAsc(Long recordId);

	Optional<ActualVisit> findByIdAndRecordId(Long id, Long recordId);

	boolean existsByIdAndRecordId(Long id, Long recordId);

	long countByRecordId(Long recordId);

	/** 장소 행이 없는 방문 수(API-071 완성 조건). FK 가 없어 서비스에서 확인한다. */
	@Query("select count(v) from ActualVisit v where v.recordId = :recordId"
		+ " and not exists (select 1 from Place p where p.id = v.placeId)")
	long countWithoutPlace(@Param("recordId") Long recordId);

	/** 여러 기록의 실제 방문을 기록별 날짜·순서대로. 기록마다 첫 줄이 첫 방문이다. 장소 행이 없으면 regionName 이 null(방문 수에는 넣는다). */
	@Query("select new com.ssafy.yeogirok.domain.record.repository.VisitRegion(v.recordId, p.regionName)"
		+ " from ActualVisit v left join Place p on p.id = v.placeId where v.recordId in :recordIds"
		+ " order by v.recordId, v.visitDate, v.position")
	List<VisitRegion> findRegionsInOrder(@Param("recordIds") Collection<Long> recordIds);
}
