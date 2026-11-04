package com.ssafy.yeogirok.domain.auth.token;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
	@NotBlank
	String issuer,

	@NotBlank(message = "JWT_SECRET 환경 변수가 필요합니다.")
	String secret,

	@NotNull
	Duration accessTokenTtl,

	@NotNull
	Duration refreshTokenTtl
) {
}
