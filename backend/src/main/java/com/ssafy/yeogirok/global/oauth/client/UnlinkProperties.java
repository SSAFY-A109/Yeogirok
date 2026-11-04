package com.ssafy.yeogirok.global.oauth.client;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 탈퇴 시 제공자 연결 해제 설정.
 *
 * @param kakaoAdminKey      카카오 어드민 키. 비어 있으면 해제를 시도하지 않고 재시도 대상으로 남긴다(로컬 개발용).
 * @param googleClientId     구글 웹 클라이언트 ID. 앱이 보낸 serverAuthCode 를 토큰으로 바꿀 때 쓴다.
 * @param googleClientSecret 구글 웹 클라이언트 시크릿. 비어 있으면 구글 회원은 탈퇴할 수 없다(코드 교환 불가).
 * @param retryInterval      실패한 카카오 해제를 다시 시도하는 간격
 * @param maxAttempts        이 횟수만큼 실패하면 더 시도하지 않고 남겨 둔다(운영자가 확인)
 */
@ConfigurationProperties(prefix = "auth.unlink")
public record UnlinkProperties(
	String kakaoAdminKey,
	URI kakaoApiBaseUrl,
	String googleClientId,
	String googleClientSecret,
	URI googleOauthBaseUrl,
	Duration retryInterval,
	int maxAttempts
) {
}
