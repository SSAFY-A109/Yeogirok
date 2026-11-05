package com.ssafy.yeogirok.domain.room.entity;

import java.math.BigDecimal;
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
 * 여행방 후보 장소. 노션 DDL {@code room_places} 와 맞춘다. 공통 places·전역 장소 ID 는 두지 않고 담을 때의 장소 정보를
 * 후보마다 따로 저장한다. 같은 장소를 여러 번 담으면 후보도 여러 개다.
 *
 * <p>DDL 과 다른 점: travel_records 엔티티가 아직 없어 {@code source_record_id} FK 를 걸지 않는다. ENUM 타입·부분
 * 인덱스·컬럼 기본값은 H2 비호환이라 Flyway V1 에 맡긴다.
 *
 * <p>후보를 빼면 {@code removed_at} 만 채우고 행은 지우지 않는다. 일정 항목이 이 행을 계속 참조하고, 언제든 복원할 수 있다.
 */
@Getter
@Entity
@Table(name = "room_places", indexes = @Index(name = "ix_room_places_room", columnList = "room_id"), check = {
	@CheckConstraint(name = "room_places_latitude_check", constraint = "latitude BETWEEN -90 AND 90"),
	@CheckConstraint(name = "room_places_longitude_check", constraint = "longitude BETWEEN -180 AND 180"),
	@CheckConstraint(name = "room_places_provider_check",
		constraint = "(provider = 'TOUR_API' AND provider_place_id IS NOT NULL)"
			+ " OR (provider = 'MANUAL' AND provider_place_id IS NULL)")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomPlace {

	public static final int NAME_MAX_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "place_name", nullable = false, length = NAME_MAX_LENGTH)
	private String placeName;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(columnDefinition = "text")
	private String address;

	@Column(name = "region_code", length = 30)
	private String regionCode;

	/** 후보 카드에 보일 읍·면·동 이름. 확보하지 못하면 null. */
	@Column(name = "region_name", length = 100)
	private String regionName;

	@Column(name = "category_code", length = 50)
	private String categoryCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlaceProvider provider;

	@Column(name = "provider_place_id", length = 100)
	private String providerPlaceId;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "image_url", columnDefinition = "text")
	private String imageUrl;

	@Column(name = "image_attribution", columnDefinition = "text")
	private String imageAttribution;

	@Enumerated(EnumType.STRING)
	@Column(name = "parking_status", nullable = false, length = 20)
	private ParkingStatus parkingStatus = ParkingStatus.UNKNOWN;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "parking_info", nullable = false)
	private Map<String, Object> parkingInfo = new HashMap<>();

	@Column(name = "synced_at")
	private Instant syncedAt;

	@Column(name = "added_by", nullable = false)
	private Long addedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 20)
	private PlaceSourceType sourceType;

	/** COURSE 로 가져온 원본 기록. 원본이 지워져도 사본은 남는다. */
	@Column(name = "source_record_id")
	private Long sourceRecordId;

	@Column(name = "removed_at")
	private Instant removedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * API-031 지도 핀 직접 추가. 이름·좌표 검사는 서비스가 끝낸 뒤 부른다.
	 * @param regionCode 좌표로 찾은 법정동 코드(10자리), 못 찾으면 null
	 * @param regionName 좌표로 찾은 읍·면·동 이름, 못 찾으면 null
	 */
	public static RoomPlace manual(Long roomId, String name, BigDecimal latitude, BigDecimal longitude,
		String address, String regionCode, String regionName, Long addedBy, Instant now) {
		RoomPlace place = new RoomPlace();
		place.roomId = roomId;
		place.placeName = name;
		place.latitude = latitude;
		place.longitude = longitude;
		place.address = address;
		place.regionCode = regionCode;
		place.regionName = regionName;
		place.provider = PlaceProvider.MANUAL;
		place.addedBy = addedBy;
		place.sourceType = PlaceSourceType.MANUAL;
		place.createdAt = now;
		place.updatedAt = now;
		return place;
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
