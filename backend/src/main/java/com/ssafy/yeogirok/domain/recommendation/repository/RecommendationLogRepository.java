package com.ssafy.yeogirok.domain.recommendation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationEventType;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationLog;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

	Optional<RecommendationLog> findByIdAndRoomIdAndEventType(Long id, Long roomId, RecommendationEventType eventType);

	/** 이 방의 추천 기록만 찾는다. 다른 방 기록이나 없는 ID 는 결과에 없다. */
	List<RecommendationLog> findByIdInAndRoomIdAndEventType(Collection<Long> ids, Long roomId,
		RecommendationEventType eventType);
}
