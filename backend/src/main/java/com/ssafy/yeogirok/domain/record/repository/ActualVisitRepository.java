package com.ssafy.yeogirok.domain.record.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.record.entity.ActualVisit;

public interface ActualVisitRepository extends JpaRepository<ActualVisit, Long> {

	/** 날짜·날짜 안 순서대로. */
	List<ActualVisit> findByRecordIdOrderByVisitDateAscPositionAsc(Long recordId);

	/** 여러 기록의 방문을 기록별 날짜·순서대로(공개 코스 검색 API-014). */
	List<ActualVisit> findByRecordIdInOrderByRecordIdAscVisitDateAscPositionAsc(Collection<Long> recordIds);

	Optional<ActualVisit> findByIdAndRecordId(Long id, Long recordId);

	boolean existsByIdAndRecordId(Long id, Long recordId);

	/** 이 초안으로 이미 만든 방문이 있는지(API-066 초안 반영). */
	boolean existsByRecordIdAndSourceDraftKey(Long recordId, String sourceDraftKey);

	long countByRecordId(Long recordId);

	/** 이 기록의 방문에 이미 연결된 계획 항목(API-064 초안이 다시 제안하지 않게). */
	@Query("select distinct v.plannedItemId from ActualVisit v where v.recordId = :recordId and v.plannedItemId is not null")
	List<Long> findLinkedPlannedItemIds(@Param("recordId") Long recordId);

	/** 장소 행이 없는 방문 수(API-071 완성 조건). FK 가 없어 서비스에서 확인한다. */
	@Query("select count(v) from ActualVisit v where v.recordId = :recordId"
		+ " and not exists (select 1 from Place p where p.id = v.placeId)")
	long countWithoutPlace(@Param("recordId") Long recordId);

	/**
	 * 코스 공개(API-073·075). 기록의 모든 방문을 공개 선택한다. 영속성 컨텍스트를 거치지 않는 UPDATE 라 잠가 둔 기록 엔티티가
	 * 분리되지 않게 컨텍스트는 비우지 않는다. 그래서 UPDATE 전에 읽어 둔 방문 엔티티(073·075 의 snapshot)는 값이 낡는다. 바꾼
	 * 표시가 없어 덮어쓰지는 않고, 지금은 UPDATE 뒤에 다시 읽는 곳이 없다. 뒤에서 공개 선택 값을 읽어야 하면 새로 조회한다.
	 *
	 * @return 바뀐 방문 수
	 */
	@Modifying(flushAutomatically = true)
	@Query("update ActualVisit v set v.publicSelected = true where v.recordId = :recordId and v.publicSelected = false")
	int selectAllPublic(@Param("recordId") Long recordId);

	/** 여러 기록의 실제 방문을 기록별 날짜·순서대로. 기록마다 첫 줄이 첫 방문이다. 장소 행이 없으면 regionName 이 null(방문 수에는 넣는다). */
	@Query("select new com.ssafy.yeogirok.domain.record.repository.VisitRegion(v.recordId, p.regionName)"
		+ " from ActualVisit v left join Place p on p.id = v.placeId where v.recordId in :recordIds"
		+ " order by v.recordId, v.visitDate, v.position")
	List<VisitRegion> findRegionsInOrder(@Param("recordIds") Collection<Long> recordIds);
}
