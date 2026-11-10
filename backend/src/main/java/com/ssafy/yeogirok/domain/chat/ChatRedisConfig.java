package com.ssafy.yeogirok.domain.chat;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PreDestroy;

/**
 * 채팅 Redis Pub/Sub 수신 컨테이너.
 *
 * <p>받은 메시지는 스레드 하나로 받은 순서대로 연결별 보내기 줄에 넣는다(실제 쓰기는 연결마다 따로 한다, ChatConnection).
 * 기본 실행기는 메시지마다 새 스레드를 써 같은 방 메시지가 뒤바뀌어 들어갈 수 있다.
 *
 * <p>구독 실행기를 따로 정하지 않으면 컨테이너가 전달 실행기를 구독에도 쓴다. 블로킹 방식 구독이면 그 한 스레드를 계속
 * 차지해 전달이 멈추므로 분리한다. 실행기를 빈으로 등록하면 스프링 부트 기본 실행기(@Async)가 물러나므로 빈으로 두지 않고
 * 여기서 만들고 닫는다.
 */
@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class ChatRedisConfig {

	private final ThreadPoolTaskExecutor relayExecutor = singleThread();

	@Bean
	public RedisMessageListenerContainer chatListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.setTaskExecutor(relayExecutor);
		container.setSubscriptionExecutor(new SimpleAsyncTaskExecutor("chat-subscription-"));
		return container;
	}

	@PreDestroy
	void shutdown() {
		relayExecutor.shutdown();
	}

	private static ThreadPoolTaskExecutor singleThread() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setThreadNamePrefix("chat-relay-");
		executor.initialize();
		return executor;
	}
}
