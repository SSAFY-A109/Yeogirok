package com.ssafy.yeogirok.global.oauth.oidc;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 제공자별로 허용할 ID 토큰 {@code aud}. 값이 비어 있으면 기동 단계에서 실패한다.
 *
 * <p>목록인 이유: 구글은 웹·Android·iOS 클라이언트 ID 가 따로 있고, 카카오도 앱 키 종류가 여럿이다.
 */
@Validated
@ConfigurationProperties(prefix = "auth.oidc")
public record OidcProperties(
	@NotNull
	@Valid
	Provider google,

	@NotNull
	@Valid
	Provider kakao
) {

	public record Provider(
		@NotEmpty(message = "허용 audience 가 최소 하나 필요합니다.")
		List<@NotBlank(message = "허용 audience 가 비어 있습니다. 환경 변수를 확인하세요.") String> allowedAudiences
	) {
	}
}
