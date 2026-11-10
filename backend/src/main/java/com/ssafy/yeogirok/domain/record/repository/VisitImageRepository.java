package com.ssafy.yeogirok.domain.record.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.record.entity.VisitImage;

public interface VisitImageRepository extends JpaRepository<VisitImage, Long> {

	Optional<VisitImage> findByRecordIdAndImagePath(Long recordId, String imagePath);

	/** 주어진 방문 중 하나라도 연결된 사진이 있는지(API-067 삭제 거부 RE020). */
	boolean existsByVisitIdIn(Collection<Long> visitIds);

	/** 방문 안 사진(순서대로). */
	List<VisitImage> findByVisitIdOrderByPositionAsc(Long visitId);

	/** 기록의 사진 보관함(미연결 포함). 올린 순서(id). */
	List<VisitImage> findByRecordIdOrderByIdAsc(Long recordId);
}
