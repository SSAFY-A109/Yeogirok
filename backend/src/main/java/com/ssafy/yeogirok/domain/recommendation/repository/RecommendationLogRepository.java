package com.ssafy.yeogirok.domain.recommendation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationEventType;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationLog;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

	Optional<RecommendationLog> findByIdAndRoomIdAndEventType(Long id, Long roomId, RecommendationEventType eventType);
}
