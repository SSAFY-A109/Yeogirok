package com.ssafy.yeogirok.domain.statistics.entity;

import java.io.Serializable;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** footprint_statistics 기본 키 (aggregation_version, cell_id, stat_date, category_codes). */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FootprintStatisticId implements Serializable {

	@Column(name = "aggregation_version", nullable = false, length = 50)
	private String aggregationVersion;

	/** 그 집계 버전의 기본 셀. 위경도 격자면 "{위도 칸}:{경도 칸}". */
	@Column(name = "cell_id", nullable = false, length = 255)
	private String cellId;

	/** visited_at 의 Asia/Seoul 달력 날짜. 하루 1회 작성 제한의 04:00 구간과 별개다. */
	@Column(name = "stat_date", nullable = false)
	private LocalDate statDate;

	/** 중복 제거·정렬한 전체 선택 집합(JSON 배열 문자열). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "category_codes", nullable = false)
	private String categoryCodes;
}
