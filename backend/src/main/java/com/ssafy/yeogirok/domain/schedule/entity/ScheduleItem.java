package com.ssafy.yeogirok.domain.schedule.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 확정한 계획 일정 항목. 노션 DDL {@code schedule_items} 와 맞춘다. 방마다 일정은 하나라 별도 일정 테이블은 없다.
 *
 * <p>DDL 과 다른 점: {@code (room_place_id, room_id) → room_places (id, room_id)} 복합 FK 는 room_places 에
 * {@code UNIQUE (id, room_id)} 가 아직 없어 걸지 않는다. 같은 방 후보인지는 서비스가 확인한다.
 * {@code UNIQUE (room_id, visit_date, position)} 는 DEFERRABLE 이 아니라서 순서를 바꿀 때는 임시 순서로 한 번 옮긴 뒤
 * 최종 순서를 넣는다({@link #TEMPORARY_POSITION_BASE}).
 *
 * <p>후보를 빼도(room_places.removed_at) 이 행은 그대로 남고 같은 후보를 계속 가리킨다.
 *
 * <p>이동 정보({@code travel_*}, {@code distance_from_previous_meters})는 직전 지점(같은 날 앞 항목, 첫 항목이면
 * 출발 지점)에서 오는 구간이다. 첫 항목인데 출발 지점이 없으면 구간이 없어 모두 null 이다.
 */
@Getter
@Entity
@Table(name = "schedule_items",
	uniqueConstraints = @UniqueConstraint(name = "uq_schedule_items_room_date_position",
		columnNames = {"room_id", "visit_date", "position"}),
	indexes = @Index(name = "ix_schedule_items_room_place", columnList = "room_place_id"),
	check = {
		@CheckConstraint(name = "schedule_items_position_check", constraint = "position >= 1"),
		@CheckConstraint(name = "schedule_items_stay_minutes_check",
			constraint = "stay_minutes BETWEEN 1 AND " + ScheduleItem.MAX_STAY_MINUTES),
		@CheckConstraint(name = "schedule_items_travel_minutes_check",
			constraint = "travel_minutes_from_previous >= 0"),
		@CheckConstraint(name = "schedule_items_distance_check", constraint = "distance_from_previous_meters >= 0"),
		@CheckConstraint(name = "schedule_items_time_order_check",
			constraint = "arrival_at IS NULL OR departure_at IS NULL OR arrival_at <= departure_at")
	})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleItem {

	/** 체류 시간 최댓값(분). 1분 단위로 1~720분(12시간)이다(명세·ERD). */
	public static final int MAX_STAY_MINUTES = 720;

	/** 순서를 바꿀 때 잠시 옮겨 두는 순서의 시작값. 실제 순서(하루 항목 수)와 겹치지 않을 만큼 크다. */
	public static final int TEMPORARY_POSITION_BASE = 100_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "room_place_id", nullable = false)
	private Long roomPlaceId;

	@Column(name = "visit_date", nullable = false)
	private LocalDate visitDate;

	@Column(nullable = false)
	private int position;

	@Column(name = "arrival_at")
	private Instant arrivalAt;

	@Column(name = "departure_at")
	private Instant departureAt;

	@Column(name = "stay_minutes", nullable = false)
	private int stayMinutes;

	@Column(name = "is_fixed", nullable = false)
	private boolean fixed;

	@Column(name = "travel_minutes_from_previous")
	private Integer travelMinutesFromPrevious;

	@Column(name = "distance_from_previous_meters", precision = 12, scale = 2)
	private BigDecimal distanceFromPreviousMeters;

	@Column(name = "travel_data_source", length = 100)
	private String travelDataSource;

	@Column(name = "travel_data_as_of")
	private Instant travelDataAsOf;

	@Column(name = "travel_unavailable_reason", columnDefinition = "text")
	private String travelUnavailableReason;

	/** 값 검사는 서비스가 끝낸 뒤 부른다. 시각·이동 정보는 {@link #applyTiming} 으로 채운다. */
	public static ScheduleItem create(Long roomId, Long roomPlaceId, LocalDate visitDate, int position,
		int stayMinutes, boolean fixed) {
		ScheduleItem item = new ScheduleItem();
		item.roomId = roomId;
		item.roomPlaceId = roomPlaceId;
		item.visitDate = visitDate;
		item.position = position;
		item.stayMinutes = stayMinutes;
		item.fixed = fixed;
		return item;
	}

	/** 날짜·순서·후보·체류 시간·고정 여부를 바꾼다. 값 검사는 서비스가 끝낸 뒤 부른다. */
	public void place(Long roomPlaceId, LocalDate visitDate, int position, int stayMinutes, boolean fixed) {
		this.roomPlaceId = roomPlaceId;
		this.visitDate = visitDate;
		this.position = position;
		this.stayMinutes = stayMinutes;
		this.fixed = fixed;
	}

	/** 순서를 바꾸기 전에 고유 제약과 겹치지 않는 임시 순서로 옮긴다. */
	public void moveToTemporaryPosition(int index) {
		this.position = TEMPORARY_POSITION_BASE + index;
	}

	/**
	 * 계산한 도착·출발 시각과 직전 지점에서 오는 구간.
	 *
	 * @param leg 직전 지점이 없으면 null
	 */
	public void applyTiming(Instant arrivalAt, Instant departureAt, TravelLeg leg) {
		this.arrivalAt = arrivalAt;
		this.departureAt = departureAt;
		this.travelMinutesFromPrevious = leg == null ? null : leg.travelMinutes();
		this.distanceFromPreviousMeters = leg == null ? null : leg.distanceMeters();
		this.travelDataSource = leg == null ? null : leg.source();
		this.travelDataAsOf = leg == null ? null : leg.asOf();
		this.travelUnavailableReason = leg == null ? null : leg.unavailableReason();
	}

	/** 직전 지점에서 오는 구간이 있는지. 첫 항목인데 출발 지점이 없으면 false. */
	public boolean hasLegFromPrevious() {
		return travelDataSource != null || travelUnavailableReason != null;
	}

	/** 저장한 구간. 없으면 null. */
	public TravelLeg legFromPrevious() {
		if (!hasLegFromPrevious()) {
			return null;
		}
		return new TravelLeg(travelMinutesFromPrevious, distanceFromPreviousMeters, travelDataSource, travelDataAsOf,
			travelUnavailableReason);
	}
}
