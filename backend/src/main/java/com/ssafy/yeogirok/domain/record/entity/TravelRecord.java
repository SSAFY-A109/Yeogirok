package com.ssafy.yeogirok.domain.record.entity;

import java.time.Instant;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 기록(방당 1개). 노션 DDL {@code travel_records} 와 맞춘다. DDL 과 다른 점: travel_rooms FK 없음,
 * ENUM 타입·{@code reference_plan} JSON 객체 CHECK·컬럼 기본값은 H2 비호환이라 Flyway V1 에 맡긴다.
 * 파일 API 는 기록 참여자 확인에만 쓴다. 기록 생성·수정은 기록 API 에서 만든다.
 */
@Getter
@Entity
@Table(name = "travel_records",
	uniqueConstraints = @UniqueConstraint(name = "travel_records_id_room_id_key", columnNames = {"id", "room_id"}),
	check = @CheckConstraint(name = "travel_records_version_check", constraint = "version >= 1"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false, unique = true)
	private Long roomId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String description = "";

	@Column(name = "region_code", length = 30)
	private String regionCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RecordStatus status = RecordStatus.DRAFT;

	@Version
	@Column(nullable = false)
	private Integer version = 1;

	/** 기록을 만들 때 비교용으로 복사한 계획. 공개 사본이 아니다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reference_plan")
	private Map<String, Object> referencePlan;

	/** 코스 게시 시각. null 이면 미게시. */
	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public static TravelRecord create(Long roomId, String title, Instant now) {
		TravelRecord record = new TravelRecord();
		record.roomId = roomId;
		record.title = title;
		record.createdAt = now;
		record.updatedAt = now;
		return record;
	}
}
