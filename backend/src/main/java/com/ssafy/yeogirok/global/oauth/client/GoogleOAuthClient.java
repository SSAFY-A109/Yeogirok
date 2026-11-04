package com.ssafy.yeogirok.global.oauth.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 구글 연결 해제. 구글은 어드민 키 같은 방식이 없고, 해제(revoke)에 그 사용자의 구글 토큰이 필요하다.
 * 서버는 구글 토큰을 저장하지 않으므로, 탈퇴할 때 앱이 다시 로그인해 받은 serverAuthCode 를 토큰으로 바꿔 바로 해제한다.
 *
 * <ul>
 *   <li>코드 교환: POST {googleOauthBaseUrl}/token (code, client_id, client_secret, grant_type=authorization_code, redirect_uri="")</li>
 *   <li>해제: POST {googleOauthBaseUrl}/revoke (token)</li>
 * </ul>
 */
@Component
public class GoogleOAuthClient {

	/** 코드 교환 결과. 리프레시 토큰은 처음 동의할 때만 오므로 없을 수 있다. */
	public record GoogleTokens(
		@JsonProperty("access_token")
		String accessToken,

		@JsonProperty("refresh_token")
		String refreshToken,

		@JsonProperty("id_token")
		String idToken
	) {

		/** 리프레시 토큰을 해제하면 그 동의 전체가 해제된다. 없으면 액세스 토큰으로 해제한다. */
		public String revocableToken() {
			return StringUtils.hasText(refreshToken) ? refreshToken : accessToken;
		}
	}

	private final RestClient restClient;
	private final String clientId;
	private final String clientSecret;

	public GoogleOAuthClient(UnlinkProperties properties) {
		this.restClient = OAuthRestClients.create(properties.googleOauthBaseUrl().toString());
		this.clientId = properties.googleClientId();
		this.clientSecret = properties.googleClientSecret();
	}

	/** 실패(잘못된·만료된·이미 쓴 코드, 시크릿 누락)하면 예외를 던진다. */
	public GoogleTokens exchange(String serverAuthCode) {
		if (!StringUtils.hasText(clientSecret)) {
			throw new IllegalStateException("GOOGLE_WEB_CLIENT_SECRET 이 설정되지 않았습니다.");
		}

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("code", serverAuthCode);
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("grant_type", "authorization_code");
		form.add("redirect_uri", "");

		GoogleTokens tokens = restClient.post()
			.uri("/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(GoogleTokens.class);
		if (tokens == null || !StringUtils.hasText(tokens.idToken())) {
			throw new IllegalStateException("구글 코드 교환 응답에 id_token 이 없습니다.");
		}
		return tokens;
	}

	/** 실패하면 예외를 던진다. */
	public void revoke(String token) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("token", token);

		restClient.post()
			.uri("/revoke")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.toBodilessEntity();
	}
}
