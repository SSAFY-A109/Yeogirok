package com.ssafy.yeogirok.domain.region.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.region.dto.RegionListResponse;
import com.ssafy.yeogirok.domain.region.entity.Region;
import com.ssafy.yeogirok.domain.region.entity.RegionLevel;
import com.ssafy.yeogirok.domain.region.repository.RegionRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegionService {

	private final RegionRepository regionRepository;

	/**
	 * parentCode 가 없으면 시·도, 있으면 그 시·도의 시·군·구를 화면 순서대로 돌려준다.
	 * 없는 code 나 시·군·구 code 를 parentCode 로 주면 C003 이다(빈 문자열도 같다).
	 */
	@Transactional(readOnly = true)
	public RegionListResponse getRegions(String parentCode) {
		if (parentCode == null) {
			Set<String> parents = new HashSet<>(regionRepository.findParentCodes());
			return toResponse(regionRepository.findByParentCodeIsNullOrderByDisplayOrderAscCodeAsc(), parents);
		}
		Region parent = regionRepository.findById(parentCode)
			.filter(region -> region.getLevel() == RegionLevel.PROVINCE)
			.orElseThrow(() -> new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"parentCode: 시·도 지역 코드여야 합니다."));
		return toResponse(regionRepository.findByParentCodeOrderByDisplayOrderAscCodeAsc(parent.getCode()), Set.of());
	}

	private static RegionListResponse toResponse(List<Region> regions, Set<String> parents) {
		return new RegionListResponse(regions.stream()
			.map(region -> new RegionListResponse.Item(region.getCode(), region.getName(), region.getLevel(),
				region.getParentCode(), parents.contains(region.getCode())))
			.toList());
	}
}
