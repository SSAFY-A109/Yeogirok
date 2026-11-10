package com.ssafy.yeogirok.global.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공통 멱등키 처리(AC-02~07). 실제 Redis(Testcontainers, infra 로컬 compose 와 같은 이미지)로 돌린다.
 *
 * <p>스프링 컨텍스트 없이 Redis 연결만 직접 만든다. DB·시큐리티와 상관없이 멱등키 동작만 본다.
 */
@Testcontainers
class IdempotencyServiceTest {

	static final Duration RETENTION = Duration.ofHours(24);
	static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(30);

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

	static LettuceConnectionFactory connectionFactory;
	static StringRedisTemplate redis;
	static final JsonMapper JSON = JsonMapper.builder().build();

	IdempotencyService service;
	FakeFootprintStore store;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redis = new StringRedisTemplate(connectionFactory);
	}

	@AfterAll
	static void disconnect() {
		connectionFactory.destroy();
	}

	@BeforeEach
	void setUp() {
		redis.execute((RedisCallback<Void>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
		service = serviceWith(PROCESSING_TIMEOUT);
		store = new FakeFootprintStore();
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	static IdempotencyService serviceWith(Duration processingTimeout) {
		return new IdempotencyService(redis, JSON, new IdempotencyProperties(RETENTION, processingTimeout));
	}

	static IdempotencyKey key(String requestKey) {
		return new IdempotencyKey(1L, "footprint-create", "-", requestKey);
	}

	/** 발자취 저장 API 를 흉내 낸 요청·결과·저장소. */
	record CreateRequest(String body, double lat, double lng) {
	}

	record Footprint(Long id, String body) {
	}

	static class FakeFootprintStore {
		final Map<String, Footprint> rows = new ConcurrentHashMap<>();
		final AtomicLong sequence = new AtomicLong(100);
		final AtomicInteger created = new AtomicInteger();

		Footprint create(CreateRequest request) {
			created.incrementAndGet();
			Footprint footprint = new Footprint(sequence.getAndIncrement(), request.body());
			rows.put(footprint.id().toString(), footprint);
			return footprint;
		}

		Footprint find(String id) {
			return rows.get(id);
		}
	}

	Footprint create(IdempotencyKey key, CreateRequest request) {
		return service.execute(key, request, () -> store.create(request), Footprint::id, store::find);
	}

	static void assertBusiness(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run)
			.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getExceptionType()).isEqualTo(type));
	}

	final CreateRequest seoul = new CreateRequest("경복궁", 37.5796, 126.9770);
	final CreateRequest busan = new CreateRequest("해운대", 35.1587, 129.1604);

	/* ── AC-02 키와 값 ─────────────────────────────────────────── */

	@Nested
	class AC02_키와_값 {

		@Test
		void 키는_request_userId_scope_target_requestKey_형식이다() {
			assertThat(new IdempotencyKey(7L, "tip-edit", "42", "abc-1").toRedisKey())
				.isEqualTo("request:7:tip-edit:42:abc-1");
		}

		@Test
		void 값은_요청_해시와_결과_ID만_저장하고_응답_전체는_저장하지_않는다() {
			Footprint created = create(key("k1"), seoul);

			JsonNode stored = JSON.readTree(redis.opsForValue().get("request:1:footprint-create:-:k1"));
			assertThat(stored.propertyNames()).containsExactlyInAnyOrder("requestHash", "resultId");
			assertThat(stored.get("resultId").asString()).isEqualTo(created.id().toString());
			assertThat(stored.get("requestHash").asString()).matches("[0-9a-f]{64}");
		}

		@Test
		void 사용자_요청종류_대상이_다르면_같은_requestKey_라도_따로_처리한다() {
			service.execute(new IdempotencyKey(1L, "footprint-create", "-", "same"), seoul,
				() -> store.create(seoul), Footprint::id, store::find);
			service.execute(new IdempotencyKey(2L, "footprint-create", "-", "same"), busan,
				() -> store.create(busan), Footprint::id, store::find);
			service.execute(new IdempotencyKey(1L, "tip-publish", "-", "same"), busan,
				() -> store.create(busan), Footprint::id, store::find);
			service.execute(new IdempotencyKey(1L, "footprint-create", "9", "same"), busan,
				() -> store.create(busan), Footprint::id, store::find);

			assertThat(store.created).hasValue(4);
		}

		@Test
		void 빈_Idempotency_Key_는_C001_이다() {
			assertBusiness(() -> key(" "), ExceptionType.REQUIRED_VALUE_MISSING);
		}
	}

	/* ── AC-03 같은 키 + 같은 요청 ─────────────────────────────── */

	@Nested
	class AC03_같은_키_같은_요청 {

		@Test
		void 처리는_한_번만_하고_저장된_결과_ID로_다시_조회한_결과를_돌려준다() {
			Footprint first = create(key("k1"), seoul);
			Footprint second = create(key("k1"), new CreateRequest("경복궁", 37.5796, 126.9770));

			assertThat(store.created).hasValue(1);
			assertThat(second).isEqualTo(first);
		}

		@Test
		void 다시_보낼_때는_저장된_결과를_새로_조회한다() {
			Footprint first = create(key("k1"), seoul);
			// 저장 뒤 내용이 바뀌었다면 처음 응답이 아니라 지금 조회한 값이 나와야 한다(응답을 캐시하지 않음).
			store.rows.put(first.id().toString(), new Footprint(first.id(), "수정된 내용"));

			assertThat(create(key("k1"), seoul).body()).isEqualTo("수정된 내용");
		}
	}

	/* ── AC-04 같은 키 + 다른 요청 ─────────────────────────────── */

	@Nested
	class AC04_같은_키_다른_요청 {

		@Test
		void C005_를_돌려주고_처리하지_않는다() {
			create(key("k1"), seoul);

			assertBusiness(() -> create(key("k1"), busan), ExceptionType.IDEMPOTENCY_KEY_REUSED);
			assertThat(store.created).hasValue(1);
		}

		@Test
		void Map_은_키_순서와_상관없이_같은_내용이면_같은_요청이다() {
			Map<String, Object> a = new LinkedHashMap<>();
			a.put("x", 1);
			a.put("y", 2);
			Map<String, Object> b = new LinkedHashMap<>();
			b.put("y", 2);
			b.put("x", 1);

			service.execute(key("k1"), a, () -> store.create(seoul), Footprint::id, store::find);
			service.execute(key("k1"), b, () -> store.create(seoul), Footprint::id, store::find);

			assertThat(store.created).hasValue(1);
		}

		/** 049·051·054 의 placement·spatialReference 처럼 요청에 JsonNode 가 들어가는 경우. 안쪽 객체도 본다. */
		@Test
		void JSON_객체_값도_키_순서와_상관없이_같은_내용이면_같은_요청이다() {
			Map<String, Object> a = Map.of("placement", JSON.readTree("{\"x\": 1, \"pose\": {\"b\": 2, \"a\": 1}}"));
			Map<String, Object> b = Map.of("placement", JSON.readTree("{\"pose\": {\"a\": 1, \"b\": 2}, \"x\": 1}"));

			service.execute(key("k1"), a, () -> store.create(seoul), Footprint::id, store::find);
			service.execute(key("k1"), b, () -> store.create(seoul), Footprint::id, store::find);

			assertThat(store.created).hasValue(1);
		}
	}

	/* ── AC-05 처리 중 ─────────────────────────────────────────── */

	@Nested
	class AC05_처리_중 {

		@Test
		void 같은_키_요청이_처리_중이면_C018_이고_끝난_뒤에는_처음_결과를_받는다() throws Exception {
			CountDownLatch started = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);

			CompletableFuture<Footprint> first = CompletableFuture.supplyAsync(() ->
				service.execute(key("k1"), seoul, () -> {
					started.countDown();
					await(release);
					return store.create(seoul);
				}, Footprint::id, store::find));
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

			assertBusiness(() -> create(key("k1"), seoul), ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS);

			release.countDown();
			Footprint firstResult = first.get(5, TimeUnit.SECONDS);

			assertThat(create(key("k1"), seoul)).isEqualTo(firstResult);
			assertThat(store.created).hasValue(1);
		}

		@Test
		void 처리_중이라도_요청_내용이_다르면_C005_다() throws Exception {
			CountDownLatch started = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			CompletableFuture<Footprint> first = CompletableFuture.supplyAsync(() ->
				service.execute(key("k1"), seoul, () -> {
					started.countDown();
					await(release);
					return store.create(seoul);
				}, Footprint::id, store::find));
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

			assertBusiness(() -> create(key("k1"), busan), ExceptionType.IDEMPOTENCY_KEY_REUSED);

			release.countDown();
			first.get(5, TimeUnit.SECONDS);
		}
	}

	/* ── AC-06 처리 실패 ───────────────────────────────────────── */

	@Nested
	class AC06_처리_실패 {

		@Test
		void 처리가_실패하면_처리_중_표시를_지워_같은_키로_다시_시도할_수_있다() {
			assertThatThrownBy(() -> service.execute(key("k1"), seoul, () -> {
				throw new IllegalStateException("외부 서비스 오류");
			}, Footprint::id, store::find)).hasMessage("외부 서비스 오류");
			assertThat(redis.hasKey("request:1:footprint-create:-:k1")).isFalse();

			Footprint retried = create(key("k1"), seoul);

			assertThat(retried).isNotNull();
			assertThat(store.created).hasValue(1);
		}

		@Test
		void 업무_오류로_실패해도_표시를_지운다() {
			assertBusiness(() -> service.execute(key("k1"), seoul, () -> {
				throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE);
			}, Footprint::id, store::find), ExceptionType.NOT_ALLOWED_VALUE);

			assertThat(redis.hasKey("request:1:footprint-create:-:k1")).isFalse();
		}
	}

	/* ── AC-07 보관 기간과 처리 중 표시 수명 ───────────────────── */

	@Nested
	class AC07_보관_기간과_표시_수명 {

		@Test
		void 처리가_끝난_키는_보관_기간_동안_남는다() {
			create(key("k1"), seoul);

			Long ttl = redis.getExpire("request:1:footprint-create:-:k1", TimeUnit.SECONDS);
			assertThat(ttl).isBetween(RETENTION.toSeconds() - 5, RETENTION.toSeconds());
		}

		@Test
		void 처리_중_표시는_표시_수명만큼만_남는다() {
			AtomicLong ttlDuringProcessing = new AtomicLong();

			service.execute(key("k1"), seoul, () -> {
				ttlDuringProcessing.set(redis.getExpire("request:1:footprint-create:-:k1", TimeUnit.SECONDS));
				return store.create(seoul);
			}, Footprint::id, store::find);

			assertThat(ttlDuringProcessing.get())
				.isBetween(PROCESSING_TIMEOUT.toSeconds() - 5, PROCESSING_TIMEOUT.toSeconds());
		}

		@Test
		void 처리하던_서버가_죽어도_표시_수명이_지나면_같은_키로_다시_처리할_수_있다() throws Exception {
			IdempotencyService shortLived = serviceWith(Duration.ofSeconds(1));
			// 처리 중 표시만 남기고 끝나지 않은 상황(서버가 처리 도중 죽음)을 만든다.
			redis.opsForValue().set("request:1:footprint-create:-:k1",
				JSON.writeValueAsString(IdempotencyRecord.processing(hashOf(shortLived, seoul))),
				Duration.ofSeconds(1));

			assertBusiness(() -> shortLived.execute(key("k1"), seoul, () -> store.create(seoul), Footprint::id,
				store::find), ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS);

			Thread.sleep(1500);

			assertThat(shortLived.execute(key("k1"), seoul, () -> store.create(seoul), Footprint::id, store::find))
				.isNotNull();
			assertThat(store.created).hasValue(1);
		}
	}

	/** 같은 요청의 해시를 서비스가 계산한 그대로 얻는다(처리를 한 번 돌리고 저장된 값을 읽는다). */
	static String hashOf(IdempotencyService service, Object request) {
		service.execute(new IdempotencyKey(0L, "hash", "-", "probe"), request, () -> "x", r -> r, id -> id);
		String stored = redis.opsForValue().get("request:0:hash:-:probe");
		return JSON.readTree(stored).get("requestHash").asString();
	}

	static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
