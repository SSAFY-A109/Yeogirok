package com.ssafy.yeogirok.domain.recommendation.dto;

import java.util.List;

/** API-033 검사를 끝낸 요청. categoryCodes 는 중복을 합친 1개 이상의 공통 카테고리 코드다. */
public record PreferenceSaveCommand(int version, String regionCode, List<String> categoryCodes) {

	public PreferenceSaveCommand {
		categoryCodes = List.copyOf(categoryCodes);
	}
}
