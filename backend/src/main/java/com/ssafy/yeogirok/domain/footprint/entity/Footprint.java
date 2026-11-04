package com.ssafy.yeogirok.domain.footprint.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 발자취(방명록). 노션 DDL {@code footprints} 와 맞춘다. DDL 과 다른 점: users FK 없음, 부분 인덱스·
 * {@code category_codes} 배열 CHECK·컬럼 기본값은 H2 비호환이라 Flyway V1 에 맡긴다.
 */
@Getter
@Entity
@Table(name = "footprints", check = {
	@CheckConstraint(name = "footprints_body_check", constraint = "char_length(body) <= 200"),
	@CheckConstraint(name = "footprints_latitude_check", constraint = "latitude BETWEEN -90 AND 90"),
	@CheckConstraint(name = "footprints_longitude_check", constraint = "longitude BETWEEN -180 AND 180"),
	@CheckConstraint(name = "footprints_version_check", constraint = "version >= 1")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Footprint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(name = "location_name", length = 255)
	private String locationName;

	@Column(columnDefinition = "text")
	private String address;

	/** 역지오코딩한 법정동·법정리 코드. 판정하지 못하면 null. */
	@Column(name = "legal_area_code", length = 10)
	private String legalAreaCode;

	@Column(columnDefinition = "text")
	private String body;

	/** 공통 카테고리 코드. 사용자가 고른 순서대로 저장한다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "category_codes", nullable = false)
	private List<String> categoryCodes;

	@Column(name = "visited_at", nullable = false)
	private Instant visitedAt;

	@Column(name = "location_captured_at", nullable = false)
	private Instant locationCapturedAt;

	@Column(name = "location_policy_version", nullable = false, length = 50)
	private String locationPolicyVersion;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	@Column(nullable = false)
	private Integer version = 1;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** 본문·카테고리는 서비스가 {@link FootprintBody}·{@link FootprintCategories} 로 정리·검사한 값을 받는다. 시각은 서비스 시계. */
	@Builder
	private Footprint(Long userId, BigDecimal latitude, BigDecimal longitude, String locationName, String address,
		String legalAreaCode, String body, List<String> categoryCodes, Instant visitedAt, Instant locationCapturedAt,
		String locationPolicyVersion, Instant createdAt) {
		this.userId = userId;
		this.latitude = latitude;
		this.longitude = longitude;
		this.locationName = locationName;
		this.address = address;
		this.legalAreaCode = legalAreaCode;
		this.body = body;
		this.categoryCodes = categoryCodes;
		this.visitedAt = visitedAt;
		this.locationCapturedAt = locationCapturedAt;
		this.locationPolicyVersion = locationPolicyVersion;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public boolean isOwnedBy(Long userId) {
		return this.userId.equals(userId);
	}

	/**
	 * API-044. 본문만 바꾼다. 같은 본문이어도 updatedAt 이 바뀌므로 UPDATE 가 나가 version 이 오른다.
	 * @param normalizedBody {@link FootprintBody#normalize} 로 정리한 본문
	 */
	public void updateBody(String normalizedBody, Instant now) {
		this.body = normalizedBody;
		this.updatedAt = now;
	}

	/** API-045. 소프트 삭제. 목록·상세·통계·작성 제한 검사에서 빠진다. */
	public void softDelete(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
	}
}
