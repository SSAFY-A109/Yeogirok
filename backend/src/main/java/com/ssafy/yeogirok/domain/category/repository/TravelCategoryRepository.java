package com.ssafy.yeogirok.domain.category.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.category.entity.TravelCategoryEntity;

public interface TravelCategoryRepository extends JpaRepository<TravelCategoryEntity, String> {

	/** 표시 순서가 같으면 코드 순으로 둔다. */
	List<TravelCategoryEntity> findAllByOrderByDisplayOrderAscCodeAsc();
}
