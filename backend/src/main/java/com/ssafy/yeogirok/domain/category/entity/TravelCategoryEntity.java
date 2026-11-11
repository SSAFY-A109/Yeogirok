package com.ssafy.yeogirok.domain.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통 카테고리 기준 데이터. 노션 DDL {@code travel_categories} 와 맞춘다. name 은 화면 표시명(API-079 name)이다.
 * 코드 이름이 같은 enum {@link com.ssafy.yeogirok.domain.category.TravelCategory} 와 겹쳐 엔티티는 Entity 를 붙였다.
 *
 * <p>행은 Flyway V2 가 넣는다(8종). 표시 순서 열은 ERD 에 없어 두지 않고 TravelCategory 선언 순서를 쓴다.
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
