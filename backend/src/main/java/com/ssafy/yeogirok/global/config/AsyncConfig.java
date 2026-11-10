package com.ssafy.yeogirok.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async 를 켠다(API-049 커밋 뒤 팁 게시). 실행기는 스프링 부트 기본 applicationTaskExecutor 다. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
