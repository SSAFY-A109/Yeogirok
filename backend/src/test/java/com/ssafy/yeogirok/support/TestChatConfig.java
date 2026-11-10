package com.ssafy.yeogirok.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ssafy.yeogirok.domain.chat.service.ChatRoomLock;

/** 채팅 테스트용 빈. H2 에는 advisory lock 이 없어 운영 잠금(PostgresChatRoomLock) 대신 JVM 잠금을 쓴다. */
@TestConfiguration
public class TestChatConfig {

	@Bean
	@Primary
	ChatRoomLock inMemoryChatRoomLock() {
		return new InMemoryChatRoomLock();
	}
}
