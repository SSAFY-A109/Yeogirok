package com.ssafy.yeogirok.global.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 프로필별 Redis 연결 정보와 멱등키 설정값(AC-01, AC-07).
 *
 * <p>OS 환경 변수가 섞이지 않도록 빈 Environment 에 프로필 yaml 과 테스트용 환경 변수만 올린다.
 */
class RedisProfileConfigTest {

	static ConfigurableEnvironment environment(String profileFile, Map<String, Object> envVars) throws IOException {
		ConfigurableEnvironment env = new AbstractEnvironment() {
		};
		new YamlPropertySourceLoader().load(profileFile, new ClassPathResource(profileFile))
			.forEach(env.getPropertySources()::addLast);
		env.getPropertySources().addFirst(new MapPropertySource("env", envVars));
		return env;
	}

	static IdempotencyProperties bind(ConfigurableEnvironment env) {
		return Binder.get(env).bindOrCreate("yeogirok.idempotency", IdempotencyProperties.class);
	}

	@Nested
	class local_프로필 {

		@Test
		void 환경_변수가_없으면_localhost_6379_비밀번호_없음으로_연결한다() throws IOException {
			ConfigurableEnvironment env = environment("application-local.yaml", Map.of());

			assertThat(env.getProperty("spring.data.redis.host")).isEqualTo("localhost");
			assertThat(env.getProperty("spring.data.redis.port")).isEqualTo("6379");
			assertThat(env.getProperty("spring.data.redis.password")).isEmpty();
		}

		@Test
		void 환경_변수가_있으면_그_값을_쓴다() throws IOException {
			ConfigurableEnvironment env = environment("application-local.yaml",
				Map.of("REDIS_HOST", "redis.internal", "REDIS_PORT", "6380"));

			assertThat(env.getProperty("spring.data.redis.host")).isEqualTo("redis.internal");
			assertThat(env.getProperty("spring.data.redis.port")).isEqualTo("6380");
		}

		@Test
		void 멱등키_보관_기간은_24시간_처리_중_표시_수명은_30초다() throws IOException {
			IdempotencyProperties properties = bind(environment("application-local.yaml", Map.of()));

			assertThat(properties.retention()).isEqualTo(Duration.ofHours(24));
			assertThat(properties.processingTimeout()).isEqualTo(Duration.ofSeconds(30));
		}
	}

	@Nested
	class prod_프로필 {

		static final Map<String, Object> ALL = Map.of(
			"REDIS_HOST", "redis", "REDIS_PORT", "6379", "REDIS_PASSWORD", "secret",
			"IDEMPOTENCY_RETENTION", "24h", "IDEMPOTENCY_PROCESSING_TIMEOUT", "30s");

		@Test
		void 환경_변수가_모두_있으면_그_값을_쓴다() throws IOException {
			ConfigurableEnvironment env = environment("application-prod.yaml", ALL);

			assertThat(env.getProperty("spring.data.redis.host")).isEqualTo("redis");
			assertThat(env.getProperty("spring.data.redis.password")).isEqualTo("secret");
			assertThat(bind(env).retention()).isEqualTo(Duration.ofHours(24));
		}

		@Test
		void Redis_환경_변수가_없으면_실패한다() throws IOException {
			for (String name : new String[] {"REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD"}) {
				Map<String, Object> envVars = new HashMap<>(ALL);
				envVars.remove(name);
				ConfigurableEnvironment env = environment("application-prod.yaml", envVars);
				String property = "spring.data.redis." + name.substring("REDIS_".length()).toLowerCase();

				assertThatThrownBy(() -> env.getProperty(property))
					.as(name)
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining(name);
			}
		}

		@Test
		void 멱등키_설정값이_없으면_실패한다() throws IOException {
			for (String name : new String[] {"IDEMPOTENCY_RETENTION", "IDEMPOTENCY_PROCESSING_TIMEOUT"}) {
				Map<String, Object> envVars = new HashMap<>(ALL);
				envVars.remove(name);
				ConfigurableEnvironment env = environment("application-prod.yaml", envVars);

				assertThatThrownBy(() -> bind(env)).as(name).hasStackTraceContaining(name);
			}
		}
	}

	@Nested
	class 설정값_검사 {

		@Test
		void 값이_아예_없으면_실패한다() {
			Binder empty = new Binder(new MapConfigurationPropertySource(Map.of()));

			assertThatThrownBy(() -> empty.bindOrCreate("yeogirok.idempotency", IdempotencyProperties.class))
				.hasStackTraceContaining("yeogirok.idempotency.retention 값이 필요합니다");
		}

		@Test
		void 값이_0_이하면_실패한다() {
			assertThatThrownBy(() -> new IdempotencyProperties(Duration.ZERO, Duration.ofSeconds(30)))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new IdempotencyProperties(Duration.ofHours(24), Duration.ofSeconds(-1)))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}
}
