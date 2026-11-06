package com.ssafy.yeogirok.domain.category;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.entity.TravelCategoryEntity;
import com.ssafy.yeogirok.domain.category.repository.TravelCategoryRepository;

import lombok.RequiredArgsConstructor;

/** 카테고리 코드 → 표시명(travel_categories.name). 없는 코드는 결과에 없다(부르는 쪽에서 null). */
@Component
@RequiredArgsConstructor
public class CategoryNames {

	private final TravelCategoryRepository travelCategoryRepository;

	/** null 코드는 건너뛴다. 코드가 하나도 없으면 조회하지 않는다. */
	@Transactional(readOnly = true)
	public Map<String, String> of(Collection<String> codes) {
		var distinct = codes.stream().filter(Objects::nonNull).distinct().toList();
		if (distinct.isEmpty()) {
			return Map.of();
		}
		return travelCategoryRepository.findAllById(distinct).stream()
			.collect(Collectors.toMap(TravelCategoryEntity::getCode, TravelCategoryEntity::getName));
	}
}
