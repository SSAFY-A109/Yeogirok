package com.ssafy.yeogirok.domain.record.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.record.entity.ActualVisit;

public interface ActualVisitRepository extends JpaRepository<ActualVisit, Long> {

	/** 날짜·날짜 안 순서대로. */
	List<ActualVisit> findByRecordIdOrderByVisitDateAscPositionAsc(Long recordId);

	Optional<ActualVisit> findByIdAndRecordId(Long id, Long recordId);
}
