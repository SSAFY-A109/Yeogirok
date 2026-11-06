package com.ssafy.yeogirok.domain.place.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통 장소. 노션 DDL {@code places} 와 맞춘다. 후보(room_places)와 실제 방문(actual_visits)이 place_id 로 참조한다.
 * <ul>
 *   <li>투어API 장소는 provider+providerPlaceId 로 한 행만 두고 재사용한다(UNIQUE).</li>
 *   <li>직접 입력(MANUAL)은 providerPlaceId 가 null 이라 입력할 때마다 새 행이다.</li>
 * </ul>
 *
 * <p>CHECK 이름에 {@code places_provider_check}·{@code places_parking_status_check} 를 쓰지 않는다. PostgreSQL 이
 * enum 컬럼 자동 CHECK 에 붙이는 이름이라 겹치면 테이블이 만들어지지 않는다. ENUM 타입·컬럼 기본값은 H2 비호환이라
 * Flyway V1 에 맡긴다.
 */
@Getter
@Entity
@Table(name = "places",
	uniqueConstraints = @UniqueConstraint(name = "uk_places_provider_place",
		columnNames = {"provider", "provider_place_id"}),
	check = {
		@CheckConstraint(name = "places_name_check", constraint = "char_length(btrim(name)) > 0"),
		@CheckConstraint(name = "places_latitude_check", constraint = "latitude BETWEEN -90 AND 90"),
		@CheckConstraint(name = "places_longitude_check", constraint = "longitude BETWEEN -180 AND 180"),
		@CheckConstraint(name = "places_provider_place_id_check",
			constraint = "(provider = 'TOUR_API' AND provider_place_id IS NOT NULL)"
				+ " OR (provider = 'MANUAL' AND provider_place_id IS NULL)")
	})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

	public static final int NAME_MAX_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlaceProvider provider;

	@Column(name = "provider_place_id", length = 100)
	private String providerPlaceId;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(columnDefinition = "text")
	private String address;

	/** 10자리 법정동 코드. 확보하지 못하면 null. */
	@Column(name = "region_code", length = 30)
	private String regionCode;

	/** 후보 카드에 보일 읍·면·동 이름. 확보하지 못하면 null. */
	@Column(name = "region_name", length = 100)
	private String regionName;

	@Column(name = "category_code", length = 50)
	private String categoryCode;

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

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * 직접 입력 장소(API-031 지도 핀). 이름·좌표 검사는 부르는 쪽이 끝낸 뒤 부른다.
	 * @param regionCode 좌표로 찾은 법정동 코드, 못 찾으면 null
	 * @param regionName 좌표로 찾은 읍·면·동 이름, 못 찾으면 null
	 */
	public static Place manual(String name, BigDecimal latitude, BigDecimal longitude, String address,
		String regionCode, String regionName, Instant now) {
		Place place = new Place();
		place.provider = PlaceProvider.MANUAL;
		place.name = name;
		place.latitude = latitude;
		place.longitude = longitude;
		place.address = address;
		place.regionCode = regionCode;
		place.regionName = regionName;
		place.createdAt = now;
		return place;
	}
}
