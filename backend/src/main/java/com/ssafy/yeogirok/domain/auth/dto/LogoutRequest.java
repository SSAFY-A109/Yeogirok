package com.ssafy.yeogirok.domain.auth.dto;

import com.ssafy.yeogirok.domain.push.dto.PushTokens;
import com.ssafy.yeogirok.global.request.JsonFields;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

/**
 * API-007 요청.
 *
 * @param refreshToken 폐기할 이 기기의 리프레시 토큰
 * @param pushToken    선택. 이 기기의 FCM 토큰. 주면 로그아웃과 함께 지운다(앱이 API-096 을 빠뜨려도 로그아웃한 기기에
 *                     채팅 미리보기가 가지 않게). 생략·null 이면 지우지 않는다
 */
public record LogoutRequest(
	@NotBlank(message = "refreshToken 은 필수입니다.")
	String refreshToken,
	JsonNode pushToken
) {

	/** 생략·null 이면 null. 문자열 아님 C002, 빈 값·공백만·4,096자 초과 C003(API-096 과 같다). */
	public String toPushToken() {
		if (JsonFields.isAbsent(pushToken)) {
			return null;
		}
		return PushTokens.validate(JsonFields.requiredString(pushToken, "pushToken"));
	}
}
