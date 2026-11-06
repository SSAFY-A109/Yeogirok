package com.ssafy.yeogirok.domain.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.category.entity.TravelCategoryEntity;

public interface TravelCategoryRepository extends JpaRepository<TravelCategoryEntity, String> {
}
