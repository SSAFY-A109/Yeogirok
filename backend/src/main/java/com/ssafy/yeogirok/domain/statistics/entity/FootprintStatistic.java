package com.ssafy.yeogirok.domain.statistics.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탈퇴자 보존분 발자취 집계(노션 DDL {@code footprint_statistics}). 탈퇴 처리가 쓰고 API-046 은 읽기만 한다.
 * DDL 과 다른 점: 인덱스 {@code ix_footprint_statistics_date_cell} 과 updated_at 기본값은 Flyway V1 에 맡긴다.
 */
@Getter
@Entity
@Table(name = "footprint_statistics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FootprintStatistic {

	@EmbeddedId
	private FootprintStatisticId id;

	@Column(name = "center_latitude", nullable = false, precision = 10, scale = 7)
	private BigDecimal centerLatitude;

	@Column(name = "center_longitude", nullable = false, precision = 10, scale = 7)
	private BigDecimal centerLongitude;

	@Column(name = "visit_count", nullable = false)
	private Long visitCount;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public FootprintStatistic(FootprintStatisticId id, BigDecimal centerLatitude, BigDecimal centerLongitude,
		Long visitCount) {
		this.id = id;
		this.centerLatitude = centerLatitude;
		this.centerLongitude = centerLongitude;
		this.visitCount = visitCount;
	}
}
