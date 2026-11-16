package com.ssafy.yeogirok.domain.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 지역 기준 데이터(API-078). code 는 법정동 코드 앞자리로 시·도 2자리, 시·군·구 5자리다.
 * 시·도는 parentCode 가 null 이고, 시·군·구는 속한 시·도 code 를 parentCode 로 갖는다.
 *
 * <p>행은 Flyway V4 가 넣는다. 화면 순서는 displayOrder 이고 시·도끼리, 같은 시·도의 시·군·구끼리 매긴다.
 */
@Getter
@Entity
@Table(name = "regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

	@Id
	@Column(length = 5)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RegionLevel level;

	@Column(name = "parent_code", length = 5)
	private String parentCode;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	private Region(String code, String name, RegionLevel level, String parentCode, int displayOrder) {
		this.code = code;
		this.name = name;
		this.level = level;
		this.parentCode = parentCode;
		this.displayOrder = displayOrder;
	}

	public static Region province(String code, String name, int displayOrder) {
		return new Region(code, name, RegionLevel.PROVINCE, null, displayOrder);
	}

	public static Region district(String code, String name, String parentCode, int displayOrder) {
		return new Region(code, name, RegionLevel.DISTRICT, parentCode, displayOrder);
	}
}
