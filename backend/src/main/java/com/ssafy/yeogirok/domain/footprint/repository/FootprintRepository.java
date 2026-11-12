package com.ssafy.yeogirok.domain.footprint.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;

import jakarta.persistence.LockModeType;

public interface FootprintRepository extends JpaRepository<Footprint, Long>, JpaSpecificationExecutor<Footprint> {

	Optional<Footprint> findByIdAndDeletedAtIsNull(Long id);

	/** API-008 activityCounts.footprints. 사용자의 삭제하지 않은 발자취 수. */
	long countByUserIdAndDeletedAtIsNull(Long userId);

	/** 삭제하지 않은 발자취를 행 잠금(SELECT ... FOR UPDATE)으로 읽는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Footprint> findForUpdateByIdAndDeletedAtIsNull(Long id);

	/** API-041 작성 제한 검사용. 사용자의 삭제하지 않은 발자취 중 서버 생성 시각이 [from, to) 인 것. */
	@Query("select f from Footprint f where f.userId = :userId and f.deletedAt is null"
		+ " and f.createdAt >= :from and f.createdAt < :to")
	List<Footprint> findActiveCreatedBetween(@Param("userId") Long userId, @Param("from") Instant from,
		@Param("to") Instant to);
}
