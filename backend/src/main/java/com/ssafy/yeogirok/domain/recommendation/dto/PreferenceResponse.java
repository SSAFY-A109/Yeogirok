package com.ssafy.yeogirok.domain.recommendation.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.recommendation.RecommendationPreferences;

/** API-033·081 응답. 저장한 적 없으면 regionCode null·categoryCodes 빈 목록·version 1 이다. */
public record PreferenceResponse(Long roomId, Values preferences, int version) {

	public static PreferenceResponse of(Long roomId, RecommendationPreferences preferences) {
		return new PreferenceResponse(roomId,
			new Values(preferences.regionCode(), preferences.categoryCodes(), false), preferences.version());
	}

	/** skipped 는 늘 false 다(건너뛰기 없음). */
	public record Values(String regionCode, List<String> categoryCodes, boolean skipped) {
	}
}
