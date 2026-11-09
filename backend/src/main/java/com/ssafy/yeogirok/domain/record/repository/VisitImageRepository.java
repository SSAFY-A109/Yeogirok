package com.ssafy.yeogirok.domain.record.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.record.entity.VisitImage;

public interface VisitImageRepository extends JpaRepository<VisitImage, Long> {

	Optional<VisitImage> findByRecordIdAndImagePath(Long recordId, String imagePath);

	/** 기록의 사진 보관함(미연결 포함). 올린 순서(id). */
	List<VisitImage> findByRecordIdOrderByIdAsc(Long recordId);
}
