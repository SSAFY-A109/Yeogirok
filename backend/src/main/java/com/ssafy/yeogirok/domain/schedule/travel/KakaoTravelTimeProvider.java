package com.ssafy.yeogirok.domain.schedule.travel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.geo.GeoDistance;
import com.ssafy.yeogirok.global.geo.KakaoLocalProperties;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 카카오 길찾기로 구한 이동 시간. 키는 카카오 로컬 API 와 같은 REST API 키다. x 는 경도, y 는 위도다.
 * <ul>
 *   <li>CAR: 카카오모빌리티 자동차 길찾기 {@code GET /v1/directions}(추천 경로 요약)</li>
 *   <li>TRANSIT: 카카오맵 대중교통 경로 조회 {@code GET /v2/routing/publictraffic}. 경로가 여러 개면 가장 빠른 것.
 *   대중교통 경로가 없고 두 지점이 {@link #WALK_FALLBACK_MAX_METERS} 안이면 도보 경로 조회로 대신한다</li>
 * </ul>
 * 출발 시각을 넣을 수 없어 시간대별 교통 상황·배차는 반영하지 않는다. 키·좌표는 로그에 남기지 않는다.
 *
 * <p>호출이 계속 실패하면 잠시 부르지 않는다. 한 요청이 구간마다 제한 시간을 다 기다리지 않게 하려는 것이다.
 * <ul>
 *   <li>401·403(키 거부, 제품 사용 설정 꺼짐 등): {@link #KEY_REJECTED_PAUSE} 동안</li>
 *   <li>그 밖의 실패(시간 초과·5xx 등)가 {@link #FAILURE_THRESHOLD} 번 이어지면: {@link #FAILURE_PAUSE} 동안</li>
 * </ul>
 * 쉬는 동안은 {@link #REASON_SUSPENDED} 로 이동 시간을 모르는 것으로 둔다. 성공하면 실패 횟수를 0 으로 되돌린다.
 *
 * <p>빈은 {@link TravelTimeConfig} 가 캐시로 감싸 등록한다.
 */
@Slf4j
public class KakaoTravelTimeProvider implements TravelTimeProvider {

	public static final String SOURCE_CAR = "KAKAO_CAR";
	public static final String SOURCE_TRANSIT = "KAKAO_TRANSIT";
	public static final String SOURCE_WALK = "KAKAO_WALK";

	/** 키가 없어 조회하지 않았다. */
	public static final String REASON_KEY_MISSING = "KAKAO_KEY_MISSING";
	/** 호출이 실패했다(시간 초과·HTTP 오류 등). */
	public static final String REASON_CALL_FAILED = "KAKAO_CALL_FAILED";
	/** 앞선 호출이 계속 실패해 잠시 부르지 않았다. */
	public static final String REASON_SUSPENDED = "KAKAO_SUSPENDED";

	/** 키가 거부되면(401·403) 쉬는 시간. 키·콘솔 설정은 금방 바뀌지 않는다(제안값). */
	static final Duration KEY_REJECTED_PAUSE = Duration.ofMinutes(10);
	/** 이 횟수만큼 실패가 이어지면 쉰다(제안값). */
	static final int FAILURE_THRESHOLD = 3;
	/** 실패가 이어졌을 때 쉬는 시간(제안값). */
	static final Duration FAILURE_PAUSE = Duration.ofMinutes(1);

	/** 대중교통 경로가 없을 때 도보로 대신하는 최대 직선거리. 이보다 멀면 이동 시간을 모르는 것으로 둔다. */
	static final double WALK_FALLBACK_MAX_METERS = 2_000;

	private static final String CAR_PATH = "/v1/directions";
	private static final String TRANSIT_PATH = "/v2/routing/publictraffic";
	private static final String WALK_PATH = "/v2/routing/walk";
	/** 자동차 길찾기에서 출발지와 도착지가 5m 안이라 경로를 찾지 않았다는 결과 코드. */
	private static final int CAR_SAME_POINT = 104;

	private final RestClient localClient;
	private final RestClient mobilityClient;
	private final KakaoLocalProperties properties;
	private final Clock clock;
	/** 자동차(카카오모빌리티)와 대중교통·도보(카카오맵)는 콘솔 사용 설정이 따로라 쉬는 상태도 따로 둔다. */
	private final Breaker mobilityBreaker = new Breaker();
	private final Breaker localBreaker = new Breaker();

	public KakaoTravelTimeProvider(KakaoLocalProperties properties, KakaoMobilityProperties mobilityProperties,
		Clock clock) {
		this(createRestClient(properties, properties.baseUrl()),
			createRestClient(properties, mobilityProperties.baseUrl()), properties, clock);
	}

	KakaoTravelTimeProvider(RestClient localClient, RestClient mobilityClient, KakaoLocalProperties properties,
		Clock clock) {
		this.localClient = localClient;
		this.mobilityClient = mobilityClient;
		this.properties = properties;
		this.clock = clock;
		if (!properties.hasRestApiKey()) {
			log.warn("KAKAO_REST_API_KEY 가 없어 길찾기를 하지 않습니다. 일정의 이동 시간과 그 뒤 도착·출발 시각은 null 로 저장됩니다.");
		}
	}

	private static RestClient createRestClient(KakaoLocalProperties properties, String baseUrl) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

	@Override
	public TravelLeg estimate(GeoPoint from, GeoPoint to, TransportMode mode) {
		String source = mode == TransportMode.CAR ? SOURCE_CAR : SOURCE_TRANSIT;
		Instant now = clock.instant();
		if (!properties.hasRestApiKey()) {
			return TravelLeg.unavailable(source, REASON_KEY_MISSING, now);
		}
		if (now.isBefore((mode == TransportMode.CAR ? mobilityBreaker : localBreaker).suspendedUntil)) {
			return TravelLeg.unavailable(source, REASON_SUSPENDED, now);
		}
		return mode == TransportMode.CAR ? car(from, to, now) : transit(from, to, now);
	}

	private TravelLeg car(GeoPoint from, GeoPoint to, Instant now) {
		JsonNode response = get(mobilityClient, mobilityBreaker, CAR_PATH, uri -> uri
			.queryParam("origin", xy(from))
			.queryParam("destination", xy(to))
			.queryParam("summary", true));
		if (response == null) {
			return TravelLeg.unavailable(SOURCE_CAR, REASON_CALL_FAILED, now);
		}
		JsonNode route = first(response.get("routes"));
		int resultCode = route == null ? -1 : route.path("result_code").asInt(-1);
		if (resultCode == CAR_SAME_POINT) {
			return TravelLeg.found(0, BigDecimal.ZERO.setScale(2), SOURCE_CAR, now);
		}
		JsonNode summary = route == null ? null : route.get("summary");
		if (resultCode != 0 || summary == null) {
			return TravelLeg.unavailable(SOURCE_CAR, "KAKAO_CAR_RESULT_" + resultCode, now);
		}
		return TravelLeg.found(minutes(summary.path("duration").asLong()), meters(summary.path("distance").asLong()),
			SOURCE_CAR, now);
	}

	private TravelLeg transit(GeoPoint from, GeoPoint to, Instant now) {
		JsonNode response = get(localClient, localBreaker, TRANSIT_PATH, uri -> uri
			.queryParam("start_x", from.longitude().toPlainString())
			.queryParam("start_y", from.latitude().toPlainString())
			.queryParam("end_x", to.longitude().toPlainString())
			.queryParam("end_y", to.latitude().toPlainString()));
		if (response == null) {
			return TravelLeg.unavailable(SOURCE_TRANSIT, REASON_CALL_FAILED, now);
		}
		String status = response.path("status").asString("");
		if ("EQUAL_POINTS".equals(status)) {
			return TravelLeg.found(0, BigDecimal.ZERO.setScale(2), SOURCE_TRANSIT, now);
		}
		JsonNode fastest = null;
		JsonNode routes = "OK".equals(status) ? response.get("routes") : null;
		for (int i = 0; routes != null && i < routes.size(); i++) {
			JsonNode properties = routes.get(i).get("properties");
			if (properties != null && properties.has("totalTime")
				&& (fastest == null || properties.path("totalTime").asLong() < fastest.path("totalTime").asLong())) {
				fastest = properties;
			}
		}
		if (fastest != null) {
			return TravelLeg.found(minutes(fastest.path("totalTime").asLong()),
				meters(fastest.path("totalDistance").asLong()), SOURCE_TRANSIT, now);
		}
		String reason = "KAKAO_TRANSIT_" + (status.isEmpty() ? "NO_RESULTS" : status);
		if (GeoDistance.meters(from.latitude(), from.longitude(), to.latitude(), to.longitude())
			<= WALK_FALLBACK_MAX_METERS) {
			TravelLeg walk = walk(from, to, now);
			if (walk != null) {
				return walk;
			}
		}
		return TravelLeg.unavailable(SOURCE_TRANSIT, reason, now);
	}

	/** 도보 경로. 찾지 못하면 null. */
	private TravelLeg walk(GeoPoint from, GeoPoint to, Instant now) {
		JsonNode response = get(localClient, localBreaker, WALK_PATH, uri -> uri
			.queryParam("start_x", from.longitude().toPlainString())
			.queryParam("start_y", from.latitude().toPlainString())
			.queryParam("end_x", to.longitude().toPlainString())
			.queryParam("end_y", to.latitude().toPlainString()));
		if (response == null) {
			return null;
		}
		String status = response.path("status").asString("");
		if ("SAME_POINT".equals(status)) {
			return TravelLeg.found(0, BigDecimal.ZERO.setScale(2), SOURCE_WALK, now);
		}
		JsonNode properties = response.path("route").get("properties");
		if (!"OK".equals(status) || properties == null || !properties.has("totalTime")) {
			return null;
		}
		return TravelLeg.found(minutes(properties.path("totalTime").asLong()),
			meters(properties.path("totalDistance").asLong()), SOURCE_WALK, now);
	}

	/** 응답 본문. 호출이 실패하면 null. */
	private JsonNode get(RestClient client, Breaker breaker, String path, UnaryOperator<UriBuilder> query) {
		try {
			JsonNode body = client.get()
				.uri(uri -> query.apply(uri.path(path)).build())
				.header("Authorization", "KakaoAK " + properties.restApiKey())
				.retrieve()
				.body(JsonNode.class);
			breaker.failures.set(0);
			return body;
		} catch (RuntimeException e) {
			recordFailure(breaker, path, e);
			return null;
		}
	}

	/** 키 거부는 바로, 그 밖의 실패는 이어질 때 잠시 쉰다. */
	private void recordFailure(Breaker breaker, String path, RuntimeException e) {
		// 예외 메시지에는 요청 주소(좌표)가 들어갈 수 있어 종류·상태 코드만 남긴다.
		int status = e instanceof RestClientResponseException responseException
			? responseException.getStatusCode().value() : -1;
		String reason = status > 0 ? "HTTP " + status : e.getClass().getSimpleName();
		if (status == 401 || status == 403) {
			breaker.suspendedUntil = clock.instant().plus(KEY_REJECTED_PAUSE);
			log.error("카카오 {} 호출이 거부됐습니다({}). 키·제품 사용 설정을 확인하세요. {} 동안 길찾기를 하지 않습니다.", path,
				reason, KEY_REJECTED_PAUSE);
			return;
		}
		if (breaker.failures.incrementAndGet() >= FAILURE_THRESHOLD) {
			breaker.failures.set(0);
			breaker.suspendedUntil = clock.instant().plus(FAILURE_PAUSE);
			log.warn("카카오 {} 호출이 {}번 이어서 실패했습니다({}). {} 동안 길찾기를 하지 않습니다.", path, FAILURE_THRESHOLD,
				reason, FAILURE_PAUSE);
			return;
		}
		log.warn("카카오 {} 호출에 실패해 이동 시간을 null 로 둡니다: {}", path, reason);
	}

	/** 제품별 연속 실패 횟수와 쉬는 기한. */
	private static final class Breaker {

		private final AtomicInteger failures = new AtomicInteger();
		private volatile Instant suspendedUntil = Instant.MIN;
	}

	private static JsonNode first(JsonNode array) {
		return array != null && array.isArray() && !array.isEmpty() ? array.get(0) : null;
	}

	private static String xy(GeoPoint point) {
		return point.longitude().toPlainString() + "," + point.latitude().toPlainString();
	}

	/** 초 → 분(올림). 1초라도 걸리면 1분이다. */
	static int minutes(long seconds) {
		return (int) Math.ceilDiv(Math.max(seconds, 0), 60);
	}

	private static BigDecimal meters(long meters) {
		return BigDecimal.valueOf(Math.max(meters, 0)).setScale(2, RoundingMode.UNNECESSARY);
	}
}
