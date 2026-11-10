package com.ssafy.yeogirok.domain.record.entity;

import java.time.Instant;
import java.time.LocalDate;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실제 방문. 노션 DDL {@code actual_visits} 와 맞춘다. DDL 과 다른 점: FK(travel_records·places) 없음,
 * {@code UNIQUE (record_id, visit_date, position) DEFERRABLE} 은 JPA 로 만들 수 없어 일반 인덱스로 두고 Flyway V1 에 맡긴다.
 * ENUM 타입·컬럼 기본값은 H2 비호환이라 Flyway V1 에 맡긴다.
 */
@Getter
@Entity
@Table(name = "actual_visits",
	uniqueConstraints = {
		@UniqueConstraint(name = "actual_visits_id_record_id_key", columnNames = {"id", "record_id"}),
		@UniqueConstraint(name = "actual_visits_record_id_source_draft_key_source_item_id_key",
			columnNames = {"record_id", "source_draft_key", "source_item_id"})
	},
	indexes = {
		@Index(name = "ix_actual_visits_place", columnList = "place_id"),
		@Index(name = "ix_actual_visits_record_date_position", columnList = "record_id, visit_date, position")
	},
	check = {
		@CheckConstraint(name = "actual_visits_position_check", constraint = "position >= 1"),
		@CheckConstraint(name = "actual_visits_source_item_id_check",
			constraint = "source_item_id IS NULL OR source_item_id >= 1"),
		@CheckConstraint(name = "actual_visits_source_check",
			constraint = "(source_draft_key IS NULL AND source_item_id IS NULL)"
				+ " OR (source_draft_key IS NOT NULL AND source_item_id IS NOT NULL)")
	})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActualVisit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "record_id", nullable = false)
	private Long recordId;

	@Column(name = "place_id", nullable = false)
	private Long placeId;

	/** reference_plan 안의 계획 항목 ID. FK 아님. */
	@Column(name = "planned_item_id")
	private Long plannedItemId;

	@Enumerated(EnumType.STRING)
	@Column(name = "creation_type", nullable = false, length = 20)
	private VisitCreationType creationType;

	@Column(name = "visit_date", nullable = false)
	private LocalDate visitDate;

	@Column(name = "visited_at", nullable = false)
	private Instant visitedAt;

	/** 그 날짜 안의 순서(1부터). */
	@Column(nullable = false)
	private Integer position;

	@Column(name = "is_public_selected", nullable = false)
	private boolean publicSelected;

	@Column(name = "confirmed_at", nullable = false)
	private Instant confirmedAt;

	@Column(name = "source_draft_key", length = 64)
	private String sourceDraftKey;

	@Column(name = "source_item_id")
	private Integer sourceItemId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** 사용자가 직접 넣은 방문(API-066). 위치는 {@link #moveTo} 로 정한다. */
	public static ActualVisit manual(Long recordId, Long placeId, LocalDate visitDate, Instant visitedAt, Instant now) {
		ActualVisit visit = new ActualVisit();
		visit.recordId = recordId;
		visit.placeId = placeId;
		visit.creationType = VisitCreationType.USER;
		visit.visitDate = visitDate;
		visit.visitedAt = visitedAt;
		visit.publicSelected = true;
		visit.confirmedAt = now;
		visit.createdAt = now;
		visit.updatedAt = now;
		return visit;
	}

	/** @return 값이 하나라도 바뀌었으면 true */
	public boolean change(Long placeId, LocalDate visitDate, Instant visitedAt, Instant now) {
		if (this.placeId.equals(placeId) && this.visitDate.equals(visitDate) && this.visitedAt.equals(visitedAt)) {
			return false;
		}
		this.placeId = placeId;
		this.visitDate = visitDate;
		this.visitedAt = visitedAt;
		this.updatedAt = now;
		return true;
	}

	/** 날짜 안의 위치만 바꾼다. 날짜 변경은 {@link #change} 가 한다. */
	public void moveTo(int position, Instant now) {
		this.position = position;
		this.updatedAt = now;
	}
}
