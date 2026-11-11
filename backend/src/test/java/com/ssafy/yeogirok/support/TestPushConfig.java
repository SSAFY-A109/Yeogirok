package com.ssafy.yeogirok.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 푸시 테스트용 빈. 실제 FCM 을 부르지 않고 보낸 알림을 쌓아 둔다. {@code PushSender} 로 주입받는 쪽(ChatPushService)이
 * 운영 빈(키가 없어 NoOpPushSender) 대신 이 가짜를 받는다.
 */
@TestConfiguration
public class TestPushConfig {

	@Bean
	@Primary
	FakePushSender fakePushSender() {
		return new FakePushSender();
	}
}
