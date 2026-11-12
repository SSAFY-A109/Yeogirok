package com.ssafy.yeogirok.domain.recommendation.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 결과·담기 이력. 노션 DDL {@code recommendation_logs} 와 맞춘다. REQUEST 행의 ID 가 API-034 의
 * recommendationRequestId 다. 같은 멱등키 재전송은 result_payload 로 같은 응답을 다시 만든다.
 *
 * <p>테이블은 Flyway {@code V3__add_recommendation_logs.sql} 이 만든다. 이 엔티티를 바꾸면 새 번호 파일도 함께 낸다.
 * DDL 과 다른 점: users·travel_rooms FK 는 엔티티 관계 없이 ID 만 둔다(다른 도메인과 같다). ENUM 타입·컬럼 기본값은
 * V1 과 같은 기준으로 넣지 않았다(backend/docs/08-flyway.md).
 */
@Getter
@Entity
@Table(name = "recommendation_logs",
	indexes = @Index(name = "ix_recommendation_logs_room", columnList = "room_id, id"),
	check = {
	@CheckConstraint(name = "recommendation_logs_rank_check", constraint = "rank IS NULL OR rank >= 1"),
	@CheckConstraint(name = "recommendation_logs_event_check",
		constraint = "(event_type = 'REQUEST' AND place_key IS NULL AND mode IS NOT NULL)"
			+ " OR (event_type <> 'REQUEST' AND place_key IS NOT NULL)")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 20)
	private RecommendationEventType eventType;

	/** ADD·REMOVE 의 추천 대상 식별값. REQUEST 는 null. */
	@Column(name = "place_key", length = 255)
	private String placeKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "place_snapshot", nullable = false)
	private Map<String, Object> placeSnapshot = new HashMap<>();

	@Column(name = "rank")
	private Integer rank;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private RecommendationMode mode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RecommendationLogStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_payload", nullable = false)
	private Map<String, Object> inputPayload = new HashMap<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_payload", nullable = false)
	private Map<String, Object> resultPayload = new HashMap<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * API-029 추천 장소 담기 한 건. 담은 장소·순위·방식은 원래 추천 요청에서 가져온다.
	 *
	 * @param placeKey 추천 대상 식별값({@code TOUR_API:providerPlaceId})
	 * @param input    원래 추천 요청 ID 와 새로 만든 후보 ID
	 */
	public static RecommendationLog add(Long roomId, Long userId, String placeKey, Map<String, Object> placeSnapshot,
		int rank, RecommendationMode mode, Map<String, Object> input, Instant now) {
		RecommendationLog log = new RecommendationLog();
		log.roomId = roomId;
		log.userId = userId;
		log.eventType = RecommendationEventType.ADD;
		log.placeKey = placeKey;
		log.placeSnapshot = new HashMap<>(placeSnapshot);
		log.rank = rank;
		log.mode = mode;
		log.status = RecommendationLogStatus.SUCCEEDED;
		log.inputPayload = new HashMap<>(input);
		log.createdAt = now;
		return log;
	}

	/** API-034 추천 요청 한 건(동기 처리, 성공). */
	public static RecommendationLog request(Long roomId, Long userId, RecommendationMode mode,
		Map<String, Object> input, Map<String, Object> result, Instant now) {
		RecommendationLog log = new RecommendationLog();
		log.roomId = roomId;
		log.userId = userId;
		log.eventType = RecommendationEventType.REQUEST;
		log.mode = mode;
		log.status = RecommendationLogStatus.SUCCEEDED;
		log.inputPayload = new HashMap<>(input);
		log.resultPayload = new HashMap<>(result);
		log.createdAt = now;
		return log;
	}
}
