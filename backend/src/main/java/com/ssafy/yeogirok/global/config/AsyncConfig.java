package com.ssafy.yeogirok.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async 를 켠다(API-049·054 팁 게시·수정, API-089 기록 사진 파일 삭제). 실행기는 스프링 부트 기본 applicationTaskExecutor 다. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
