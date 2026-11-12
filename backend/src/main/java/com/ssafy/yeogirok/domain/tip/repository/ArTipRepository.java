package com.ssafy.yeogirok.domain.tip.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;

import jakarta.persistence.LockModeType;

public interface ArTipRepository extends JpaRepository<ArTip, Long> {

	Optional<ArTip> findByIdAndStatusAndDeletedAtIsNull(Long id, TipStatus status);

	Optional<ArTip> findByIdAndDeletedAtIsNull(Long id);

	/** 작성자의 삭제되지 않은 팁 중 status 가 statuses 인 것의 수. */
	long countByAuthorIdAndStatusInAndDeletedAtIsNull(Long authorId, Collection<TipStatus> statuses);

	/** 팁 행을 잠가 읽는다(SELECT ... FOR UPDATE). 삭제·게시·수정이 한 줄로 서서 삭제한 팁을 되살리지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from ArTip t where t.id = :id")
	Optional<ArTip> findForUpdateById(@Param("id") Long id);

	/** 게시 작업이 끊겨 REGISTERING 에 남은 팁 후보. 잠근 뒤 다시 확인한다. */
	@Query("select t.id from ArTip t where t.status = com.ssafy.yeogirok.domain.tip.entity.TipStatus.REGISTERING"
		+ " and t.deletedAt is null and t.updatedAt < :cutoff")
	List<Long> findStuckRegisteringIds(@Param("cutoff") Instant cutoff);

	/** API-053. 작성자의 삭제되지 않은 팁을 (createdAt, id) 내림차순으로, 커서 (at, id) 다음부터. */
	@Query("select t from ArTip t where t.authorId = :authorId and t.deletedAt is null and t.status in :statuses"
		+ " and (t.createdAt < :at or (t.createdAt = :at and t.id < :id)) order by t.createdAt desc, t.id desc")
	List<ArTip> findMine(@Param("authorId") Long authorId, @Param("statuses") Collection<TipStatus> statuses,
		@Param("at") Instant at, @Param("id") Long id, Limit limit);

	/** API-047 후보. 공개 팁 중 위경도 사각형 안(경계 포함)인 것. category 가 null 이면 전체. 거리는 서비스가 다시 잰다. */
	@Query("select t from ArTip t where t.status = com.ssafy.yeogirok.domain.tip.entity.TipStatus.PUBLISHED"
		+ " and t.deletedAt is null and t.latitude between :south and :north and t.longitude between :west and :east"
		+ " and (:category is null or t.category = :category)")
	List<ArTip> findPublicInBox(@Param("south") BigDecimal south, @Param("north") BigDecimal north,
		@Param("west") BigDecimal west, @Param("east") BigDecimal east, @Param("category") ArTipCategory category);
}
