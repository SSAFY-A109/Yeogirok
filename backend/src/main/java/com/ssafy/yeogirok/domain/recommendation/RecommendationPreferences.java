package com.ssafy.yeogirok.domain.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여행방 추천 조건({@code travel_rooms.preferences} JSON). 저장한 적 없으면 {@link #EMPTY}(조건 version 1)다.
 *
 * @param regionCode    시/군/구 법정동 코드 5자리. 저장 전이면 null
 * @param categoryCodes 공통 카테고리 코드(중복 없음, 선택한 순서). 저장 전이면 빈 목록
 * @param version       조건 version. 저장할 때마다 1 오른다. 여행방 version 과 별개다
 */
public record RecommendationPreferences(String regionCode, List<String> categoryCodes, int version) {

	public static final int INITIAL_VERSION = 1;
	public static final RecommendationPreferences EMPTY = new RecommendationPreferences(null, List.of(), INITIAL_VERSION);

	static final String REGION_CODE = "regionCode";
	static final String CATEGORY_CODES = "categoryCodes";
	static final String SKIPPED = "skipped";
	static final String VERSION = "version";

	public RecommendationPreferences {
		categoryCodes = List.copyOf(categoryCodes);
	}

	/** 저장된 JSON 을 읽는다. 비었거나 모양이 다른 값은 저장 전으로 본다. */
	public static RecommendationPreferences from(Map<String, Object> json) {
		if (json == null || json.isEmpty()) {
			return EMPTY;
		}
		String regionCode = json.get(REGION_CODE) instanceof String value ? value : null;
		List<String> categoryCodes = new ArrayList<>();
		if (json.get(CATEGORY_CODES) instanceof List<?> list) {
			for (Object code : list) {
				if (code instanceof String value) {
					categoryCodes.add(value);
				}
			}
		}
		int version = json.get(VERSION) instanceof Number number ? number.intValue() : INITIAL_VERSION;
		return new RecommendationPreferences(regionCode, categoryCodes, version);
	}

	/** 추천을 실행할 수 있는지(지역과 카테고리 1개 이상이 있다). */
	public boolean isReady() {
		return regionCode != null && !categoryCodes.isEmpty();
	}

	/** 다음 조건. version 이 1 오른다. */
	public RecommendationPreferences next(String regionCode, List<String> categoryCodes) {
		return new RecommendationPreferences(regionCode, categoryCodes, version + 1);
	}

	public Map<String, Object> toJson() {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put(REGION_CODE, regionCode);
		json.put(CATEGORY_CODES, categoryCodes);
		json.put(SKIPPED, false);
		json.put(VERSION, version);
		return json;
	}
}
