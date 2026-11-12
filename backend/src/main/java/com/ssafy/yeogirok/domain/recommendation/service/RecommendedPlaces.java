package com.ssafy.yeogirok.domain.recommendation.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse.RecommendedPlace;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationEventType;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationLog;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationMode;
import com.ssafy.yeogirok.domain.recommendation.repository.RecommendationLogRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천 장소 담기(API-029 sourceType=AI)가 쓰는 추천 기록. 담으려는 장소가 이 방의 추천 결과에 그 순위로 정말 있었는지
 * 확인하고, 담은 뒤 ADD 기록을 남긴다. 장소 정보는 추천 결과(result_payload)에 저장한 값을 그대로 쓴다(TourAPI 재호출 없음).
 */
@Component
@RequiredArgsConstructor
public class RecommendedPlaces {

	private final RecommendationLogRepository recommendationLogRepository;
	private final JsonMapper jsonMapper;

	/** 담으려는 추천 장소 하나. */
	public record Pick(Long recommendationRequestId, int rank, String providerPlaceId) {
	}

	/** 확인을 마친 추천 장소. place 는 추천 결과에 저장된 장소 정보다. */
	public record Verified(Pick pick, RecommendedPlace place, RecommendationMode mode) {
	}

	/**
	 * 고른 순서대로 확인 결과를 준다. 같은 방의 추천 요청 기록이 아니거나(다른 방·없는 ID), 그 순위의 장소가 요청한 장소와
	 * 다르면 C003 이다. 방의 다른 참여자가 받은 추천도 담을 수 있다(추천 결과는 방 단위로 공유한다).
	 */
	@Transactional(readOnly = true)
	public List<Verified> verify(Long roomId, List<Pick> picks) {
		Map<Long, RecommendationLog> logs = recommendationLogRepository.findByIdInAndRoomIdAndEventType(
				picks.stream().map(Pick::recommendationRequestId).collect(Collectors.toSet()), roomId,
				RecommendationEventType.REQUEST).stream()
			.collect(Collectors.toMap(RecommendationLog::getId, log -> log));
		Map<Long, PlaceRecommendationResponse> results = new LinkedHashMap<>();
		return picks.stream().map(pick -> {
			RecommendationLog log = logs.get(pick.recommendationRequestId());
			if (log == null) {
				throw notAllowed("recommendationRequestId: 이 여행방의 추천 결과가 아닙니다: " + pick.recommendationRequestId());
			}
			PlaceRecommendationResponse result = results.computeIfAbsent(log.getId(),
				id -> jsonMapper.convertValue(log.getResultPayload(), PlaceRecommendationResponse.class));
			RecommendedPlace place = rankedPlace(result, pick.rank())
				.filter(found -> PlaceProvider.TOUR_API.name().equals(found.provider())
					&& pick.providerPlaceId().equals(found.providerPlaceId()))
				.orElseThrow(() -> notAllowed("rank: 추천 결과의 " + pick.rank() + "위 장소가 아닙니다."));
			return new Verified(pick, place, log.getMode());
		}).toList();
	}

	/** 담은 후보마다 ADD 기록을 남긴다. 후보를 저장한 트랜잭션 안에서 부른다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordAdd(Long roomId, Long userId, Verified verified, Long roomPlaceId, Instant now) {
		@SuppressWarnings("unchecked")
		Map<String, Object> snapshot = jsonMapper.convertValue(verified.place(), Map.class);
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("recommendationRequestId", verified.pick().recommendationRequestId());
		input.put("roomPlaceId", roomPlaceId);
		recommendationLogRepository.save(RecommendationLog.add(roomId, userId,
			PlaceProvider.TOUR_API.name() + ":" + verified.pick().providerPlaceId(), snapshot,
			verified.pick().rank(), verified.mode(), input, now));
	}

	private static Optional<RecommendedPlace> rankedPlace(PlaceRecommendationResponse result, int rank) {
		return result.items().stream().filter(item -> item.rank() == rank).findFirst()
			.map(PlaceRecommendationResponse.Item::place);
	}

	private static BusinessException notAllowed(String message) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
	}
}
