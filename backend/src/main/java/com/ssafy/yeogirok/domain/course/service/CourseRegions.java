package com.ssafy.yeogirok.domain.course.service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ssafy.yeogirok.domain.place.entity.Place;

/**
 * 검색 카드의 지역명(API-014 regionName, 명세 {@code specs/course-search-api.md} 5절 4). 주소 첫 단어가 시·도 이름이면 주소의
 * 시·군·구("강원특별자치도 강릉시 …" → "강릉시"), 아니면(직접 입력 장소의 자유 주소 "우리집 근처" 등, PR #94 리뷰) 좌표로 찾은
 * 법정동 앞 2자리의 시·도 이름, 둘 다 없으면 빈 문자열.
 */
final class CourseRegions {

	/** 법정동 코드 앞 2자리 → 시·도(강원 51·전북 52). */
	private static final Map<String, String> PROVINCES = Map.ofEntries(Map.entry("11", "서울특별시"),
		Map.entry("26", "부산광역시"), Map.entry("27", "대구광역시"), Map.entry("28", "인천광역시"),
		Map.entry("29", "광주광역시"), Map.entry("30", "대전광역시"), Map.entry("31", "울산광역시"),
		Map.entry("36", "세종특별자치시"), Map.entry("41", "경기도"), Map.entry("43", "충청북도"), Map.entry("44", "충청남도"),
		Map.entry("46", "전라남도"), Map.entry("47", "경상북도"), Map.entry("48", "경상남도"),
		Map.entry("50", "제주특별자치도"), Map.entry("51", "강원특별자치도"), Map.entry("52", "전북특별자치도"));

	/** 주소 첫 단어로 쓰이는 시·도 이름(정식 이름, 줄인 이름, 바뀌기 전 이름). */
	private static final Set<String> PROVINCE_WORDS = Stream.concat(PROVINCES.values().stream(),
		Stream.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "충북", "충남", "전남", "경북", "경남",
			"제주", "강원", "전북", "강원도", "전라북도", "제주도")).collect(Collectors.toUnmodifiableSet());

	private CourseRegions() {
	}

	static String nameOf(Place place) {
		if (place == null) {
			return "";
		}
		String[] words = place.getAddress() == null ? new String[0] : place.getAddress().strip().split("\\s+");
		if (words.length > 0 && PROVINCE_WORDS.contains(words[0])) {
			// 세종처럼 시·도 아래 시·군·구가 없으면 첫 단어를 쓴다.
			return words.length > 1 && words[1].matches(".+[시군구]") ? words[1] : words[0];
		}
		String code = place.getRegionCode();
		return code == null || code.length() < 2 ? "" : PROVINCES.getOrDefault(code.substring(0, 2), "");
	}
}
