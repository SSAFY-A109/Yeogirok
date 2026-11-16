package com.ssafy.yeogirok.domain.footprint.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

	/** 탈퇴 정리용. 사용자의 삭제하지 않은 발자취를 집계에 필요한 열만. */
	@Query("select new com.ssafy.yeogirok.domain.footprint.repository.FootprintVisit("
		+ "f.latitude, f.longitude, f.visitedAt, f.categoryCodes)"
		+ " from Footprint f where f.userId = :userId and f.deletedAt is null")
	List<FootprintVisit> findActiveVisits(@Param("userId") Long userId);

	/** 탈퇴 정리 작업용. 탈퇴했는데 발자취가 남은 회원(이 정리가 생기기 전에 탈퇴한 회원). */
	@Query("select distinct f.userId from Footprint f, User u where u.id = f.userId"
		+ " and u.status = com.ssafy.yeogirok.domain.user.domain.UserStatus.WITHDRAWN")
	List<Long> findWithdrawnUserIds();

	/**
	 * 탈퇴 정리용. 사용자의 발자취를 삭제 상태까지 모두 지운다. 한 문장으로 지우되, 같은 트랜잭션에서 바꾼 것(회원 탈퇴 상태)을
	 * 먼저 내려 보낸다. 영속성 컨텍스트는 비우지 않는다(비우면 아직 내려 보내지 않은 변경이 사라질 수 있다).
	 */
	@Modifying(flushAutomatically = true)
	@Query("delete from Footprint f where f.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
