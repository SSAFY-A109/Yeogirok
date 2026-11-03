package com.ssafy.yeogirok.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 토큰 발급·만료 판단이 쓰는 시계. 테스트에서 시각을 고정하려고 빈으로 둔다. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
