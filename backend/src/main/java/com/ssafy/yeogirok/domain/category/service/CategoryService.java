package com.ssafy.yeogirok.domain.category.service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.category.dto.CategoryListResponse;
import com.ssafy.yeogirok.domain.category.entity.TravelCategoryEntity;
import com.ssafy.yeogirok.domain.category.repository.TravelCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final TravelCategoryRepository travelCategoryRepository;

	/**
	 * TravelCategory 선언 순서대로, travel_categories 에 표시명이 있는 코드만 돌려준다.
	 * 발자취 등록이 같은 enum 으로 코드를 검사하므로 목록에 나온 코드는 모두 등록할 수 있다.
	 */
	@Transactional(readOnly = true)
	public CategoryListResponse getCategories() {
		Map<String, String> names = travelCategoryRepository.findAll().stream()
			.collect(Collectors.toMap(TravelCategoryEntity::getCode, TravelCategoryEntity::getName));
		return new CategoryListResponse(Arrays.stream(TravelCategory.values())
			.map(TravelCategory::name)
			.filter(names::containsKey)
			.map(code -> new CategoryListResponse.Category(code, names.get(code)))
			.toList());
	}
}
