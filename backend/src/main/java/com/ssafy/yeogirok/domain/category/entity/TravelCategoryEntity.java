package com.ssafy.yeogirok.domain.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통 카테고리 기준 데이터. 노션 DDL {@code travel_categories} 와 맞춘다. name 은 화면 표시명(API-079 label)이다.
 * 코드 이름이 같은 enum {@link com.ssafy.yeogirok.domain.category.TravelCategory} 와 겹쳐 엔티티는 Entity 를 붙였다.
 *
 * <p>행은 아직 넣지 않는다. 코드별 표시명이 확정되지 않았다(API-079 는 예시만 있다). 행이 없으면 categoryName 은 null 이다.
 */
@Getter
@Entity
@Table(name = "travel_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelCategoryEntity {

	@Id
	@Column(length = 50)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	public TravelCategoryEntity(String code, String name) {
		this.code = code;
		this.name = name;
	}
}
