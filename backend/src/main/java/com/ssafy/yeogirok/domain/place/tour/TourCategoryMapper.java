package com.ssafy.yeogirok.domain.place.tour;

import static com.ssafy.yeogirok.domain.category.TravelCategory.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.ssafy.yeogirok.domain.category.TravelCategory;

/**
 * TourAPI 신분류체계(lclsSystm) → 공통 카테고리(11/2 백엔드 확정 매핑, 2026-11-02 조회 기준 대분류 10·중분류 59·소분류 246).
 *
 * <ol>
 *   <li>소분류 코드(lclsSystm3)가 예외 표에 있으면 그 값을 쓴다(값이 null 이면 카테고리 없음).</li>
 *   <li>없으면 중분류 코드(lclsSystm2)의 기본 카테고리를 쓴다.</li>
 *   <li>둘 다 해당하지 않으면 카테고리 없음(null). 숙박(AC)·추천코스(C01)·VE08·VE09·VE11·VE12 가 여기 든다.</li>
 * </ol>
 * 카테고리 없는 장소는 인기 장소 순위·카테고리 필터·추천에 나오지 않는다. 표를 바꾸면 저장된 places.category_code 와
 * 달라질 수 있으니 노션 매핑표를 먼저 고친다.
 */
public final class TourCategoryMapper {

	/** 중분류 → 기본 카테고리. 카테고리 없음인 중분류는 넣지 않는다. */
	private static final Map<String, TravelCategory> MIDDLE = Map.ofEntries(
		// NATURE 자연 힐링 (EX05 웰니스관광은 일부만 — 예외 표 참고)
		Map.entry("NA01", NATURE), Map.entry("NA02", NATURE), Map.entry("NA03", NATURE), Map.entry("NA04", NATURE),
		Map.entry("NA05", NATURE), Map.entry("VE03", NATURE), Map.entry("EX05", NATURE),
		// CULTURE 역사 문화 (VE07 전시시설은 컨벤션센터 제외)
		Map.entry("HS01", CULTURE), Map.entry("HS02", CULTURE), Map.entry("HS03", CULTURE), Map.entry("HS04", CULTURE),
		Map.entry("VE06", CULTURE), Map.entry("VE07", CULTURE), Map.entry("EX01", CULTURE), Map.entry("EX04", CULTURE),
		// SIGHT 랜드마크 거리
		Map.entry("VE01", SIGHT), Map.entry("VE04", SIGHT), Map.entry("VE05", SIGHT),
		// FOOD 맛집 (FD03 간이음식의 제과는 CAFE)
		Map.entry("FD01", FOOD), Map.entry("FD02", FOOD), Map.entry("FD03", FOOD), Map.entry("FD04", FOOD),
		// CAFE 카페
		Map.entry("FD05", CAFE),
		// ACTIVITY 체험 액티비티 (EX06 산업관광의 근대산업유산은 CULTURE)
		Map.entry("EX02", ACTIVITY), Map.entry("EX03", ACTIVITY), Map.entry("EX06", ACTIVITY), Map.entry("EX07", ACTIVITY),
		Map.entry("LS01", ACTIVITY), Map.entry("LS02", ACTIVITY), Map.entry("LS03", ACTIVITY), Map.entry("LS04", ACTIVITY),
		Map.entry("VE02", ACTIVITY), Map.entry("VE10", ACTIVITY),
		// SHOPPING 쇼핑 시장
		Map.entry("SH01", SHOPPING), Map.entry("SH02", SHOPPING), Map.entry("SH03", SHOPPING), Map.entry("SH04", SHOPPING),
		Map.entry("SH05", SHOPPING), Map.entry("SH06", SHOPPING), Map.entry("SH07", SHOPPING),
		// EVENT 축제 공연 행사
		Map.entry("EV01", EVENT), Map.entry("EV02", EVENT), Map.entry("EV03", EVENT));

	/** 소분류 예외 8개. 값이 null 이면 카테고리 없음(Map.of 는 null 을 못 넣어 HashMap 으로 만든다). */
	private static final Map<String, TravelCategory> EXCEPTIONS;

	static {
		Map<String, TravelCategory> exceptions = new HashMap<>();
		// 웰니스 중 자연과 거리가 먼 시설 → 체험
		exceptions.put("EX050200", ACTIVITY); // 찜질방
		exceptions.put("EX050300", ACTIVITY); // 한방체험
		exceptions.put("EX050500", ACTIVITY); // 뷰티스파
		exceptions.put("EX050600", ACTIVITY); // 기타웰니스
		exceptions.put("EX050800", null); // 기타의료관광: 의료 목적 시설
		exceptions.put("EX060100", CULTURE); // 근대산업유산: 산업 유적에 가까움
		exceptions.put("FD030100", CAFE); // 제과: 빵집·베이커리
		exceptions.put("VE070400", null); // 컨벤션센터: 행사가 열리는 장소(행사 자체는 EV03)
		EXCEPTIONS = Collections.unmodifiableMap(exceptions);
	}

	private TourCategoryMapper() {
	}

	/** @return 공통 카테고리. 카테고리 없음이거나 분류 코드가 없으면 null */
	public static TravelCategory categoryOf(String lclsSystm2, String lclsSystm3) {
		if (lclsSystm3 != null && EXCEPTIONS.containsKey(lclsSystm3)) {
			return EXCEPTIONS.get(lclsSystm3);
		}
		return lclsSystm2 == null ? null : MIDDLE.get(lclsSystm2);
	}

	/**
	 * 대분류 안에서 카테고리가 붙는 중분류 전체(카테고리 없음인 중분류 제외). 예외 소분류가 있는 중분류(EX05·EX06·FD03·VE07)도
	 * 기본값이 있어 여기 들어간다.
	 */
	public static Set<String> middleCodesIn(String lclsSystm1) {
		Set<String> codes = new LinkedHashSet<>();
		MIDDLE.keySet().stream().filter(code -> code.startsWith(lclsSystm1)).sorted().forEach(codes::add);
		return codes;
	}

	/** 이 카테고리가 될 수 있는 장소가 있는 중분류(기본값 + 예외 소분류의 중분류). TourAPI 조회 조건을 정할 때 쓴다. */
	public static Set<String> middleCodesOf(TravelCategory category) {
		Set<String> codes = new LinkedHashSet<>();
		MIDDLE.entrySet().stream().filter(entry -> entry.getValue() == category).map(Map.Entry::getKey).sorted()
			.forEach(codes::add);
		EXCEPTIONS.entrySet().stream().filter(entry -> entry.getValue() == category)
			.map(entry -> entry.getKey().substring(0, 4)).sorted().forEach(codes::add);
		return codes;
	}
}
