package com.ssafy.yeogirok.domain.region.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.region.entity.RegionLevel;

/** API-078 지역 목록. 화면 순서대로 담는다. parentCode 는 시·도이면 null 이다. */
public record RegionListResponse(List<Item> items) {

	public record Item(String code, String name, RegionLevel level, String parentCode, boolean hasChildren) {
	}
}
