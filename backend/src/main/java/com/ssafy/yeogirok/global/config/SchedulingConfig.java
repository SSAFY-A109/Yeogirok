package com.ssafy.yeogirok.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 실패한 제공자 연결 해제 재시도(ProviderUnlinkService.retryFailed)와 영수증 원본 정리(ReceiptCleanupJob)에 쓴다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
