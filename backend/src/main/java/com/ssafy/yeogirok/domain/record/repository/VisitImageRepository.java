package com.ssafy.yeogirok.domain.record.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.record.entity.VisitImage;

public interface VisitImageRepository extends JpaRepository<VisitImage, Long> {

	Optional<VisitImage> findByRecordIdAndImagePath(Long recordId, String imagePath);

	/** 주어진 방문 중 하나라도 연결된 사진이 있는지(API-067 삭제 거부 RE020). */
	boolean existsByVisitIdIn(Collection<Long> visitIds);

	/**
	 * API-089. 지운 사진 뒤의 사진을 한 칸씩 앞으로 당긴다. 영속성 컨텍스트를 거치지 않는 UPDATE 라 이 방문의 사진을 미리
	 * 읽어 두지 않은 곳에서만 쓴다. 컨텍스트는 비우지 않는다(잠가 둔 기록 엔티티가 분리되면 version 갱신이 사라진다).
	 */
	@Modifying(flushAutomatically = true)
	@Query("update VisitImage i set i.position = i.position - 1 where i.visitId = :visitId and i.position > :position")
	int pullForward(@Param("visitId") Long visitId, @Param("position") int position);

	/** 기록별 사진 수(API-058·059 progress). 사진이 없는 기록은 빠진다. */
	@Query("select new com.ssafy.yeogirok.domain.record.repository.RecordPhotoCount(i.recordId, count(i))"
		+ " from VisitImage i where i.recordId in :recordIds group by i.recordId")
	List<RecordPhotoCount> countByRecordIds(@Param("recordIds") Collection<Long> recordIds);

	/** 기록의 사진 보관함(미연결 포함). 올린 순서(id). */
	List<VisitImage> findByRecordIdOrderByIdAsc(Long recordId);
}
