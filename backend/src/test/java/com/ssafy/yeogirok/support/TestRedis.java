package com.ssafy.yeogirok.support;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * 스프링 컨텍스트 없이 쓰는 실제 Redis(Testcontainers, infra 로컬 compose 와 같은 이미지). 컨테이너는 처음 쓸 때 한 번 띄우고
 * 테스트가 끝나면 Testcontainers 가 정리한다.
 */
public final class TestRedis {

	private static GenericContainer<?> container;
	private static StringRedisTemplate template;

	private TestRedis() {
	}

	public static synchronized StringRedisTemplate template() {
		if (template == null) {
			container = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
			container.start();
			template = new StringRedisTemplate(connect(container.getHost(), container.getMappedPort(6379)));
		}
		return template;
	}

	public static void flushAll() {
		template().execute((RedisCallback<Void>)connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
	}

	/** 아무것도 듣지 않는 포트에 붙은 연결. Redis 장애 경우를 만든다. 다 쓰면 destroy 한다. */
	public static LettuceConnectionFactory unreachable() {
		return connect("localhost", 1);
	}

	private static LettuceConnectionFactory connect(String host, int port) {
		LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
		factory.afterPropertiesSet();
		factory.start();
		return factory;
	}
}
