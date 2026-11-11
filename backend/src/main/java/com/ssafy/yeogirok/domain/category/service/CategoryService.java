package com.ssafy.yeogirok.domain.category.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.dto.CategoryListResponse;
import com.ssafy.yeogirok.domain.category.repository.TravelCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private final TravelCategoryRepository travelCategoryRepository;

	/** travel_categories 의 행을 표시 순서대로 돌려준다. */
	@Transactional(readOnly = true)
	public CategoryListResponse getCategories() {
		return new CategoryListResponse(travelCategoryRepository.findAllByOrderByDisplayOrderAscCodeAsc().stream()
			.map(category -> new CategoryListResponse.Category(category.getCode(), category.getName()))
			.toList());
	}
}
