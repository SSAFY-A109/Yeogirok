package com.ssafy.yeogirok.domain.user.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원 탈퇴(API-009) 설정.
 *
 * @param reauthMaxAge 소셜 로그인(API-005) 후 이 시간 안에 받은 액세스 토큰으로만 탈퇴할 수 있다(A011).
 */
@ConfigurationProperties(prefix = "auth.withdrawal")
public record WithdrawalProperties(
	Duration reauthMaxAge
) {
}
