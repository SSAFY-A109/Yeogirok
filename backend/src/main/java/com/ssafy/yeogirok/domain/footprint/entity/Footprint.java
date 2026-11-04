package com.ssafy.yeogirok.domain.footprint.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
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
 * 발자취(방명록). 노션 ERD 실행용 DDL(10/30) {@code footprints} 와 컬럼 이름·타입·NOT NULL·길이를 맞춘다.
 *
 * <p>DDL 과 다른 점(Flyway V1 이 DDL 로 만들 때 채워진다):
 * <ul>
 *   <li>{@code user_id} 는 연관관계 없는 Long 이라 users FK 가 없다(User 엔티티 없음).</li>
 *   <li>부분 인덱스 2개({@code WHERE deleted_at IS NULL})와 {@code category_codes} 배열 CHECK·컬럼 기본값은
 *       H2 테스트 DB 와 호환되지 않아 엔티티에 두지 않는다. 값은 항상 애플리케이션이 채운다.</li>
 * </ul>
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

	/** 역지오코딩으로 판정한 법정동·법정리 코드. 판정하지 못하면 null. 작성 제한(반경 500m)에는 쓰지 않는다. */
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

	/** DDL 기본값과 같게 1부터 시작한다. */
	@Version
	@Column(nullable = false)
	private Integer version = 1;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** 본문은 {@link FootprintBody}, 카테고리는 {@link FootprintCategories} 규칙으로 정리·검사한다. */
	@Builder
	private Footprint(Long userId, BigDecimal latitude, BigDecimal longitude, String locationName, String address,
		String legalAreaCode, String body, List<String> categoryCodes, Instant visitedAt, Instant locationCapturedAt,
		String locationPolicyVersion) {
		this.userId = userId;
		this.latitude = latitude;
		this.longitude = longitude;
		this.locationName = locationName;
		this.address = address;
		this.legalAreaCode = legalAreaCode;
		this.body = FootprintBody.normalize(body);
		this.categoryCodes = FootprintCategories.validate(categoryCodes);
		this.visitedAt = visitedAt;
		this.locationCapturedAt = locationCapturedAt;
		this.locationPolicyVersion = locationPolicyVersion;
	}

	public boolean isOwnedBy(Long userId) {
		return this.userId.equals(userId);
	}

	/** API-044. 본문만 바꾼다. 위치·방문 시각·카테고리는 그대로다. 빈 본문은 null 이 된다. */
	public void updateBody(String body) {
		this.body = FootprintBody.normalize(body);
		// 같은 본문이어도 수정 성공이면 version +1(명세 AC-01). 값이 바뀌지 않으면 Hibernate 가 UPDATE 를 건너뛰므로 직접 표시한다.
		this.updatedAt = Instant.now();
	}

	/** API-045. 소프트 삭제. 행은 남기고 목록·상세·통계·작성 제한 검사에서 뺀다. version 은 UPDATE 때 1 오른다. */
	public void softDelete(Instant now) {
		this.deletedAt = now;
	}
}
