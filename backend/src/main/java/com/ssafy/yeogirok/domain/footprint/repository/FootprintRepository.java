package com.ssafy.yeogirok.domain.footprint.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;

import jakarta.persistence.LockModeType;

public interface FootprintRepository extends JpaRepository<Footprint, Long>, JpaSpecificationExecutor<Footprint> {

	Optional<Footprint> findByIdAndDeletedAtIsNull(Long id);

	/** 삭제하지 않은 발자취를 행 잠금(SELECT ... FOR UPDATE)으로 읽는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Footprint> findForUpdateByIdAndDeletedAtIsNull(Long id);
}
