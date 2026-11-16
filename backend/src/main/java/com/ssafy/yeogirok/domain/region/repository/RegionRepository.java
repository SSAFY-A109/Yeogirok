package com.ssafy.yeogirok.domain.region.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ssafy.yeogirok.domain.region.entity.Region;
import com.ssafy.yeogirok.domain.region.entity.RegionLevel;

public interface RegionRepository extends JpaRepository<Region, String> {

	/** 추천 조건 저장(API-033)이 regionCode 가 목록의 시·군·구인지 확인한다. */
	boolean existsByCodeAndLevel(String code, RegionLevel level);

	List<Region> findByParentCodeIsNullOrderByDisplayOrderAscCodeAsc();

	List<Region> findByParentCodeOrderByDisplayOrderAscCodeAsc(String parentCode);

	/** 시·군·구를 하나 이상 가진 시·도 code. hasChildren 계산용. */
	@Query("select distinct r.parentCode from Region r where r.parentCode is not null")
	List<String> findParentCodes();
}
