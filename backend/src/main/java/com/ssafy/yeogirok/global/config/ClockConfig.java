package com.ssafy.yeogirok.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 서버 현재 시각. 토큰 만료·위치 증거 유효 시각·04:00 구간 같은 시각 의존 검사를 테스트에서 고정하려고 빈으로 둔다. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
