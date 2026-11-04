package com.ssafy.yeogirok.global.oauth.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 카카오 연결 끊기(어드민 키 방식). 사용자 액세스 토큰 없이 회원번호로 끊을 수 있다.
 * ID 토큰의 sub 가 카카오 회원번호다.
 *
 * <p>POST {kakaoApiBaseUrl}/v1/user/unlink, Authorization: KakaoAK {어드민 키},
 * target_id_type=user_id, target_id={sub}
 */
@Component
public class KakaoUnlinkClient {

	private final RestClient restClient;
	private final String adminKey;

	public KakaoUnlinkClient(UnlinkProperties properties) {
		this.restClient = OAuthRestClients.create(properties.kakaoApiBaseUrl().toString());
		this.adminKey = properties.kakaoAdminKey();
	}

	/** 실패하면 예외를 던진다. 호출하는 쪽이 재시도 대상으로 남긴다. */
	public void unlink(String kakaoUserId) {
		if (!StringUtils.hasText(adminKey)) {
			throw new IllegalStateException("KAKAO_ADMIN_KEY 가 설정되지 않았습니다.");
		}

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("target_id_type", "user_id");
		form.add("target_id", kakaoUserId);

		restClient.post()
			.uri("/v1/user/unlink")
			.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.toBodilessEntity();
	}
}
