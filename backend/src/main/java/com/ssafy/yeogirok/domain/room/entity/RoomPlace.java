package com.ssafy.yeogirok.domain.room.entity;

import java.time.Instant;

import com.ssafy.yeogirok.domain.place.entity.Place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행방 후보 장소. 노션 DDL {@code room_places} 와 맞춘다. 장소 정보는 공통 {@link Place} 에 두고 place_id 로 참조하며,
 * 이 행은 누가·어떤 경로로 담았는지와 삭제 상태만 갖는다. 같은 장소를 여러 번 담으면 후보도 여러 개다.
 *
 * <p>DDL 과 다른 점: travel_records 엔티티를 아직 참조하지 않아 {@code source_record_id} FK 를 걸지 않는다.
 * TODO 기록 도메인이 들어오면 {@code source_record_id → travel_records(id)} FK 를 건다.
 * 부분 인덱스(WHERE removed_at IS NULL)·컬럼 기본값은 H2 비호환이라 일반 인덱스로 두고 Flyway V1 에 맡긴다.
 *
 * <p>후보를 빼면 {@code removed_at} 만 채우고 행은 지우지 않는다. 일정 항목이 이 행을 계속 참조하고, 언제든 복원할 수 있다.
 */
@Getter
@Entity
@Table(name = "room_places",
	// schedule_items 의 (room_place_id, room_id) 복합 FK 가 가리킬 키(ERD).
	uniqueConstraints = @UniqueConstraint(name = "uq_room_places_id_room", columnNames = {"id", "room_id"}),
	indexes = {
	@Index(name = "ix_room_places_active_place", columnList = "room_id, place_id"),
	@Index(name = "ix_room_places_place_room", columnList = "place_id, room_id"),
	@Index(name = "ix_room_places_active_source_record", columnList = "room_id, source_record_id"),
	@Index(name = "ix_room_places_source_record_room", columnList = "source_record_id, room_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomPlace {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	/** 응답의 Place 를 만들 때 늘 같이 쓰므로 목록 조회는 리포지토리에서 함께 읽는다. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false)
	private Place place;

	@Column(name = "added_by", nullable = false)
	private Long addedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 20)
	private PlaceSourceType sourceType;

	/** COURSE 로 가져온 원본 기록. 원본이 지워져도 후보는 남는다. */
	@Column(name = "source_record_id")
	private Long sourceRecordId;

	@Column(name = "removed_at")
	private Instant removedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** API-031 지도 핀 직접 추가. {@code place} 는 새로 만든 MANUAL 장소다. */
	public static RoomPlace manual(Long roomId, Place place, Long addedBy, Instant now) {
		RoomPlace roomPlace = new RoomPlace();
		roomPlace.roomId = roomId;
		roomPlace.place = place;
		roomPlace.addedBy = addedBy;
		roomPlace.sourceType = PlaceSourceType.MANUAL;
		roomPlace.createdAt = now;
		roomPlace.updatedAt = now;
		return roomPlace;
	}

	public boolean isRemoved() {
		return removedAt != null;
	}

	/** API-032. 후보 목록에서만 뺀다. */
	public void remove(Instant now) {
		this.removedAt = now;
		this.updatedAt = now;
	}

	/** API-094. 원래 행을 되살린다(ID·출처·추가자 그대로). */
	public void restore(Instant now) {
		this.removedAt = null;
		this.updatedAt = now;
	}
}
