package com.ssafy.yeogirok.domain.tip.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ssafy.yeogirok.global.geo.GeoDistance;
import com.ssafy.yeogirok.global.location.LocationEvidence;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 현장 AR 팁. 노션 DDL {@code ar_tips} 와 맞춘다. DDL 과 다른 점: users FK 없음,
 * ENUM 타입·컬럼 기본값은 H2 비호환이라 Flyway V1 에 맡긴다.
 */
@Getter
@Entity
@Table(name = "ar_tips", check = {
	@CheckConstraint(name = "ar_tips_body_check",
		constraint = "char_length(body) <= 120 AND char_length(btrim(body)) > 0"),
	@CheckConstraint(name = "ar_tips_latitude_check", constraint = "latitude BETWEEN -90 AND 90"),
	@CheckConstraint(name = "ar_tips_longitude_check", constraint = "longitude BETWEEN -180 AND 180"),
	@CheckConstraint(name = "ar_tips_accuracy_meters_check", constraint = "accuracy_meters >= 0"),
	@CheckConstraint(name = "ar_tips_published_check",
		constraint = "status <> 'PUBLISHED' OR (spatial_reference_id IS NOT NULL AND published_at IS NOT NULL)"),
	@CheckConstraint(name = "ar_tips_version_check", constraint = "version >= 1")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArTip {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "category_code", nullable = false, length = 20)
	private ArTipCategory category;

	@Column(nullable = false, columnDefinition = "text")
	private String body;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(name = "location_captured_at", nullable = false)
	private Instant locationCapturedAt;

	@Column(name = "accuracy_meters", nullable = false, precision = 10, scale = 2)
	private BigDecimal accuracyMeters;

	@Column(name = "location_policy_version", nullable = false, length = 50)
	private String locationPolicyVersion;

	@Column(name = "spatial_provider", nullable = false, length = 50)
	private String spatialProvider;

	@Column(name = "spatial_reference_id", length = 255)
	private String spatialReferenceId;

	@Column(name = "spatial_expires_at")
	private Instant spatialExpiresAt;

	/** 앱이 정한 배치 JSON 을 그대로 저장한다(서버는 객체·크기만 확인). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, Object> placement;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipStatus status = TipStatus.REGISTERING;

	@Version
	@Column(nullable = false)
	private Integer version = 1;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * API-049. 게시 전(REGISTERING) 팁. 팁 좌표는 요청 위치(기기 GPS)다.
	 * @param body {@link TipBody#normalize} 로 정리한 본문
	 */
	public static ArTip register(Long authorId, ArTipCategory category, String body, LocationEvidence location,
		String locationPolicyVersion, SpatialReference spatialReference, Map<String, Object> placement, Instant now) {
		ArTip tip = new ArTip();
		tip.authorId = authorId;
		tip.category = category;
		tip.body = body;
		tip.latitude = location.latitude();
		tip.longitude = location.longitude();
		tip.accuracyMeters = location.accuracyMeters();
		tip.locationCapturedAt = location.capturedAt();
		tip.locationPolicyVersion = locationPolicyVersion;
		tip.spatialProvider = spatialReference.provider();
		tip.spatialReferenceId = spatialReference.referenceId();
		tip.spatialExpiresAt = spatialReference.expiresAt();
		tip.placement = placement;
		tip.createdAt = now;
		tip.updatedAt = now;
		return tip;
	}

	public void publish(Instant now) {
		this.status = TipStatus.PUBLISHED;
		this.publishedAt = now;
		this.updatedAt = now;
	}

	/**
	 * API-054 수정 적용. null 인 항목은 그대로 둔다. 사진만 바뀌어도 updatedAt 이 바뀌어 version 이 오른다.
	 * status·publishedAt 은 그대로다.
	 */
	public void edit(ArTipCategory category, String body, Instant now) {
		if (category != null) {
			this.category = category;
		}
		if (body != null) {
			this.body = body;
		}
		this.updatedAt = now;
	}

	/** API-054 AR 재배치. 좌표·위치 증거·공간 참조·배치를 함께 바꾼다. */
	public void relocate(LocationEvidence location, String locationPolicyVersion, SpatialReference spatialReference,
		Map<String, Object> placement) {
		this.latitude = location.latitude();
		this.longitude = location.longitude();
		this.accuracyMeters = location.accuracyMeters();
		this.locationCapturedAt = location.capturedAt();
		this.locationPolicyVersion = locationPolicyVersion;
		this.spatialProvider = spatialReference.provider();
		this.spatialReferenceId = spatialReference.referenceId();
		this.spatialExpiresAt = spatialReference.expiresAt();
		this.placement = placement;
	}

	/** API-051 최초 게시 재시도. 실패(FAILED)한 팁을 다시 게시 전(REGISTERING)으로 돌린다. */
	public void retryCreate(Instant now) {
		this.status = TipStatus.REGISTERING;
		this.updatedAt = now;
	}

	/** 최초 게시 실패. 공개하지 않는다. */
	public void fail(Instant now) {
		this.status = TipStatus.FAILED;
		this.updatedAt = now;
	}

	/** API-055. 소프트 삭제. 공개 검색·상세·내 목록·사진 접근에서 빠진다. */
	public void delete(Instant now) {
		this.status = TipStatus.DELETED;
		this.deletedAt = now;
		this.updatedAt = now;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public SpatialReference spatialReference() {
		return new SpatialReference(spatialProvider, spatialReferenceId, spatialExpiresAt);
	}

	public double distanceMetersFrom(BigDecimal latitude, BigDecimal longitude) {
		return GeoDistance.meters(this.latitude, this.longitude, latitude, longitude);
	}
}
