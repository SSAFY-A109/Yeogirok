package com.ssafy.yeogirok.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시각 의존 검사(위치 증거 유효 시각, 04:00 구간)를 테스트에서 고정하려고 Clock 을 빈으로 둔다. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
