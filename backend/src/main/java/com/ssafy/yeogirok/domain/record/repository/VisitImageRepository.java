package com.ssafy.yeogirok.domain.record.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.record.entity.VisitImage;

public interface VisitImageRepository extends JpaRepository<VisitImage, Long> {

	Optional<VisitImage> findByRecordIdAndImagePath(Long recordId, String imagePath);
}
