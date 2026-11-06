package com.ssafy.yeogirok.domain.room.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행방. 노션 DDL {@code travel_rooms} 와 맞춘다. DDL 과 다른 점: ENUM 타입·JSONB CHECK·컬럼 기본값은
 * H2 비호환이라 Flyway V1 에 맡긴다. 초대 링크에 시간 만료가 없어 {@code invite_expires_at} 은 두지 않는다(11/5 결정).
 *
 * <p>초대 토큰은 {@code invite_token_encrypted} 에 결정적 암호화(AES-SIV)로 저장한다(11/5 결정, ERD 의
 * {@code invite_token_hash} 에서 이름 변경). 같은 토큰은 항상 같은 암호문이라 이 값으로 방을 찾고,
 * 복호화해 기존 링크를 다시 보여 준다.
 */
@Getter
@Entity
@Table(name = "travel_rooms", check = {
	@CheckConstraint(name = "travel_rooms_dates_check", constraint = "(start_date IS NULL AND end_date IS NULL)"
		+ " OR (start_date IS NOT NULL AND end_date IS NOT NULL AND start_date <= end_date)"),
	@CheckConstraint(name = "travel_rooms_version_check", constraint = "version >= 1")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRoom {

	public static final int TITLE_MAX_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "transport_mode", nullable = false, length = 20)
	private TransportMode transportMode;

	/** 추천 조건. API-033 에서 채운다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, Object> preferences = new HashMap<>();

	/** 일정 생성 옵션. 일정 API 에서 채운다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "schedule_options", nullable = false)
	private Map<String, Object> scheduleOptions = new HashMap<>();

	@Column(name = "schedule_needs_recalculation", nullable = false)
	private boolean scheduleNeedsRecalculation;

	/** 초대 링크는 방 정보 수정과 따로 움직이므로 만들어도 version 을 올리지 않는다. */
	@OptimisticLock(excluded = true)
	@Column(name = "invite_token_encrypted", unique = true)
	private String inviteTokenEncrypted;

	/** null 이면 사용 중. 마지막 현재 참여자가 나가면 같은 트랜잭션에서 채운다. */
	@OptimisticLock(excluded = true)
	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	@Column(nullable = false)
	private Integer version = 1;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * 시각만 바뀐 것으로 version 을 올리지 않는다. 초대 링크 생성·소프트 삭제처럼 방 정보가 아닌 변경이 시각을 바꿔도
	 * 다른 참여자가 보던 version 이 낡지 않게 한다. 방 정보 수정은 다른 필드가 바뀌어 version 이 오르고,
	 * 후보 변경은 RoomVersions 가 올린다.
	 */
	@OptimisticLock(excluded = true)
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** 값 검사는 서비스가 끝낸 뒤 부른다. 시각은 서비스 시계. */
	@Builder
	private TravelRoom(String title, LocalDate startDate, LocalDate endDate, TransportMode transportMode,
		Instant createdAt) {
		this.title = title;
		this.startDate = startDate;
		this.endDate = endDate;
		this.transportMode = transportMode;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public boolean hasInviteToken() {
		return inviteTokenEncrypted != null;
	}

	/** API-023. 바꾸지 않는 값은 지금 값을 그대로 넘긴다. */
	public void updateInfo(String title, LocalDate startDate, LocalDate endDate, TransportMode transportMode,
		Instant now) {
		this.title = title;
		this.startDate = startDate;
		this.endDate = endDate;
		this.transportMode = transportMode;
		this.updatedAt = now;
	}

	/** API-024. 방당 한 번만 만든다. */
	public void assignInviteToken(String encryptedToken, Instant now) {
		if (this.inviteTokenEncrypted != null) {
			throw new IllegalStateException("이미 초대 토큰이 있는 방입니다: " + id);
		}
		this.inviteTokenEncrypted = encryptedToken;
		this.updatedAt = now;
	}

	/** API-087. 마지막 현재 참여자가 나갈 때. */
	public void softDelete(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
	}
}
