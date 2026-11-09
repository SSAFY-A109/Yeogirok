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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 기록(방당 1개). 노션 DDL {@code travel_records} 와 맞춘다. DDL 과 다른 점: travel_rooms FK 없음,
 * ENUM 타입·{@code reference_plan} JSON 객체 CHECK·컬럼 기본값은 H2 비호환이라 Flyway V1 에 맡긴다.
 *
 * <p>version 은 JPA {@code @Version} 이 아니라 직접 올린다. 방문·사진·이야기처럼 다른 테이블만 바뀌어도 기록 version 을
 * 올려야 해서다. 쓰기는 모두 기록 행을 잠근 뒤에 하므로(명세 {@code specs/record-api.md} 1-2) 동시 수정은 잠금이 막는다.
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

	public boolean isCompleted() {
		return status == RecordStatus.COMPLETED;
	}

	/** 방문·사진 연결·이야기가 실제로 바뀌었을 때만 부른다. 완성한 기록은 다시 작성 중이 된다. published_at 은 그대로 둔다. */
	public void markEdited(Instant now) {
		status = RecordStatus.DRAFT;
		version++;
		updatedAt = now;
	}

	/** API-071. 이미 완성한 기록에는 부르지 않는다. */
	public void complete(Instant now) {
		status = RecordStatus.COMPLETED;
		version++;
		updatedAt = now;
	}
}
