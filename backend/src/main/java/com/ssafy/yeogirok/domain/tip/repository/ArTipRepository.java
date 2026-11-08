package com.ssafy.yeogirok.domain.tip.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;

public interface ArTipRepository extends JpaRepository<ArTip, Long> {

	Optional<ArTip> findByIdAndStatusAndDeletedAtIsNull(Long id, TipStatus status);

	/** API-047 후보. 공개 팁 중 위경도 사각형 안(경계 포함)인 것. category 가 null 이면 전체. 거리는 서비스가 다시 잰다. */
	@Query("select t from ArTip t where t.status = com.ssafy.yeogirok.domain.tip.entity.TipStatus.PUBLISHED"
		+ " and t.deletedAt is null and t.latitude between :south and :north and t.longitude between :west and :east"
		+ " and (:category is null or t.category = :category)")
	List<ArTip> findPublicInBox(@Param("south") BigDecimal south, @Param("north") BigDecimal north,
		@Param("west") BigDecimal west, @Param("east") BigDecimal east, @Param("category") ArTipCategory category);
}
