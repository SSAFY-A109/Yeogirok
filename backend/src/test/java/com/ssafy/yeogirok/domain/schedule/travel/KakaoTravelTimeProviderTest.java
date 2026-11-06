package com.ssafy.yeogirok.domain.schedule.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.geo.KakaoLocalProperties;

/**
 * 카카오 길찾기 응답을 해석하는 규칙. 실제 카카오를 부르지 않고 응답을 흉내 낸다(응답 모양은 카카오모빌리티 자동차
 * 길찾기·카카오맵 대중교통/도보 경로 조회 문서 기준). 실제 키로 부르는 확인은 이 테스트 범위 밖이다.
 */
class KakaoTravelTimeProviderTest {

	static final GeoPoint FROM = new GeoPoint(new BigDecimal("37.5000000"), new BigDecimal("127.0300000"));
	/** FROM 에서 약 560m. */
	static final GeoPoint NEAR = new GeoPoint(new BigDecimal("37.5050000"), new BigDecimal("127.0300000"));
	/** FROM 에서 약 11km. */
	static final GeoPoint FAR = new GeoPoint(new BigDecimal("37.6000000"), new BigDecimal("127.0300000"));
	static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

	static final String CAR_URL = "https://apis-navi.kakaomobility.com/v1/directions";
	static final String TRANSIT_URL = "https://dapi.kakao.com/v2/routing/publictraffic";
	static final String WALK_URL = "https://dapi.kakao.com/v2/routing/walk";

	MockRestServiceServer localServer;
	MockRestServiceServer mobilityServer;

	private KakaoTravelTimeProvider provider(String key) {
		RestClient.Builder local = RestClient.builder().baseUrl("https://dapi.kakao.com");
		RestClient.Builder mobility = RestClient.builder().baseUrl("https://apis-navi.kakaomobility.com");
		localServer = MockRestServiceServer.bindTo(local).build();
		mobilityServer = MockRestServiceServer.bindTo(mobility).build();
		return new KakaoTravelTimeProvider(local.build(), mobility.build(),
			new KakaoLocalProperties(key, "https://dapi.kakao.com", Duration.ofSeconds(2), Duration.ofSeconds(3)),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static ResponseActions expect(MockRestServiceServer server, String url) {
		return server.expect(requestTo(Matchers.startsWith(url)))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "KakaoAK test-key"));
	}

	private static String carJson(int resultCode) {
		return """
			{"trans_id":"t","routes":[{"result_code":%d,"result_msg":"m",
			  "summary":{"distance":12345,"duration":1250}}]}
			""".formatted(resultCode);
	}

	/* ── 자동차 ─────────────────────────────────────────────── */

	@Test
	void 자동차는_추천_경로_요약의_시간을_분으로_올림한다() {
		KakaoTravelTimeProvider provider = provider("test-key");
		expect(mobilityServer, CAR_URL)
			.andExpect(queryParam("origin", "127.0300000,37.5000000"))   // 경도,위도
			.andExpect(queryParam("destination", "127.0300000,37.6000000"))
			.andRespond(withSuccess(carJson(0), MediaType.APPLICATION_JSON));

		TravelLeg leg = provider.estimate(FROM, FAR, TransportMode.CAR);

		assertThat(leg).isEqualTo(TravelLeg.found(21, new BigDecimal("12345.00"), "KAKAO_CAR", NOW));
		mobilityServer.verify();
	}

	@Test
	void 자동차_출발지와_도착지가_5m_안이면_0분이다() {
		KakaoTravelTimeProvider provider = provider("test-key");
		expect(mobilityServer, CAR_URL).andRespond(withSuccess(carJson(104), MediaType.APPLICATION_JSON));

		assertThat(provider.estimate(FROM, FROM, TransportMode.CAR).travelMinutes()).isZero();
	}

	@Test
	void 자동차_경로가_없으면_결과_코드를_사유로_남긴다() {
		KakaoTravelTimeProvider provider = provider("test-key");
		expect(mobilityServer, CAR_URL).andRespond(withSuccess(carJson(105), MediaType.APPLICATION_JSON));

		TravelLeg leg = provider.estimate(FROM, FAR, TransportMode.CAR);

		assertThat(leg).isEqualTo(TravelLeg.unavailable("KAKAO_CAR", "KAKAO_CAR_RESULT_105", NOW));
	}

	/* ── 대중교통 ───────────────────────────────────────────── */

	@Test
	void 대중교통은_가장_빠른_경로를_쓴다() {
		KakaoTravelTimeProvider provider = provider("test-key");
		expect(localServer, TRANSIT_URL)
			.andExpect(queryParam("start_x", "127.0300000"))
			.andExpect(queryParam("start_y", "37.5000000"))
			.andExpect(queryParam("end_x", "127.0300000"))
			.andExpect(queryParam("end_y", "37.6000000"))
			.andRespond(withSuccess("""
				{"status":"OK","properties":{"total":2},"routes":[
				  {"properties":{"type":"BUS","totalDistance":5013,"totalTime":2115,"transfers":0}},
				  {"properties":{"type":"SUBWAY","totalDistance":6000,"totalTime":1800,"transfers":1}}]}
				""", MediaType.APPLICATION_JSON));

		TravelLeg leg = provider.estimate(FROM, FAR, TransportMode.TRANSIT);

		assertThat(leg).isEqualTo(TravelLeg.found(30, new BigDecimal("6000.00"), "KAKAO_TRANSIT", NOW));
		localServer.verify();
	}

	@Test
	void 대중교통_경로가_없고_가까우면_도보_경로로_대신한다() {
		KakaoTravelTimeProvider provider = provider("test-key");
		expect(localServer, TRANSIT_URL).andRespond(withSuccess("{\"status\":\"NO_RESULTS\"}",
			MediaType.APPLICATION_JSON));
		expect(localServer, WALK_URL).andRespond(withSuccess("""
			{"route":{"properties":{"totalDistance":600,"totalTime":540}},"status":"OK"}
			""", MediaType.APPLICATION_JSON));

		TravelLeg leg = provider.estimate(FROM, NEAR, TransportMode.TRANSIT);

		assertThat(leg).isEqualTo(TravelLeg.found(9, new BigDecimal("600.00"), "KAKAO_WALK", NOW));
		localServer.verify();
	}

	@Test
	void 대중교통_경로가_없고_멀면_이동_시간을_모른다() {
		KakaoTravelTimeProvider provider = provider("test-key");
		expect(localServer, TRANSIT_URL).andRespond(withSuccess("{\"status\":\"NO_RESULTS\"}",
			MediaType.APPLICATION_JSON));

		TravelLeg leg = provider.estimate(FROM, FAR, TransportMode.TRANSIT);

		assertThat(leg).isEqualTo(TravelLeg.unavailable("KAKAO_TRANSIT", "KAKAO_TRANSIT_NO_RESULTS", NOW));
		localServer.verify();
	}

	/* ── 실패·키 없음 ───────────────────────────────────────── */

	@Test
	void 호출이_실패하면_예외_없이_모르는_것으로_둔다() {
		KakaoTravelTimeProvider provider = provider("test-key");
		expect(mobilityServer, CAR_URL).andRespond(withServerError());

		assertThat(provider.estimate(FROM, FAR, TransportMode.CAR))
			.isEqualTo(TravelLeg.unavailable("KAKAO_CAR", KakaoTravelTimeProvider.REASON_CALL_FAILED, NOW));
	}

	@Test
	void 키가_없으면_부르지_않는다() {
		KakaoTravelTimeProvider provider = provider("");

		assertThat(provider.estimate(FROM, FAR, TransportMode.TRANSIT))
			.isEqualTo(TravelLeg.unavailable("KAKAO_TRANSIT", KakaoTravelTimeProvider.REASON_KEY_MISSING, NOW));
		localServer.verify();
		mobilityServer.verify();
	}

	@Test
	void 초는_분으로_올림한다() {
		assertThat(KakaoTravelTimeProvider.minutes(0)).isZero();
		assertThat(KakaoTravelTimeProvider.minutes(1)).isEqualTo(1);
		assertThat(KakaoTravelTimeProvider.minutes(60)).isEqualTo(1);
		assertThat(KakaoTravelTimeProvider.minutes(61)).isEqualTo(2);
	}
}
