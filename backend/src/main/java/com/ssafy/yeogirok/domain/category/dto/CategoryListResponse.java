package com.ssafy.yeogirok.domain.category.dto;

import java.util.List;

/** API-079 카테고리 목록. 표시 순서대로 담는다. name 은 다른 API 의 categoryName 과 같은 값이다. */
public record CategoryListResponse(List<Category> categories) {

	public record Category(String code, String name) {
	}
}
