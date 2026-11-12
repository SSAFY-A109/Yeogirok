package com.ssafy.yeogirok.domain.place.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** TourAPI 응답 해석·인증키 인코딩·실패 구분. 실제 TourAPI 는 부르지 않는다. */
class HttpTourApiClientTest {

	/** Decoding 키에 흔히 들어가는 문자(+ / =)를 넣는다. */
	static final String KEY = "ab+cd/ef==";
	static final TourApiProperties PROPERTIES = new TourApiProperties(KEY, "https://tour.test/KorService2", "Yeogirok",
		Duration.ofSeconds(2), Duration.ofSeconds(3));
	static final TourAreaQuery CAFE_IN_GANGNEUNG = new TourAreaQuery("51", "150", "FD", "FD05", 50);

	MockRestServiceServer server;

	HttpTourApiClient client(TourApiProperties properties) {
		RestClient.Builder builder = HttpTourApiClient.restClientBuilder(properties);
		server = MockRestServiceServer.bindTo(builder).build();
		return new HttpTourApiClient(builder, properties);
	}

	static String body(String items) {
		return """
			{"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
			 "body":{"items":%s,"numOfRows":50,"pageNo":1,"totalCount":2}}}
			""".formatted(items);
	}

	@Test
	void 인증키는_값_전체를_인코딩하고_법정동_분류_조건을_보낸다() {
		HttpTourApiClient client = client(PROPERTIES);
		server.expect(requestTo(allOf(
				containsString("https://tour.test/KorService2/areaBasedList2?"),
				containsString("serviceKey=ab%2Bcd%2Fef%3D%3D"),
				containsString("MobileOS=ETC"), containsString("MobileApp=Yeogirok"), containsString("_type=json"),
				containsString("arrange=C"), containsString("numOfRows=50"),
				containsString("lDongRegnCd=51"), containsString("lDongSignguCd=150"),
				containsString("lclsSystm1=FD"), containsString("lclsSystm2=FD05"))))
			.andRespond(withSuccess(body("\"\""), MediaType.APPLICATION_JSON));

		client.areaBasedList(CAFE_IN_GANGNEUNG);

		server.verify();
	}

	@Test
	void 없는_조건은_보내지_않는다() {
		HttpTourApiClient client = client(PROPERTIES);
		server.expect(requestTo(allOf(not(containsString("lDongSignguCd")), not(containsString("lclsSystm2")))))
			.andRespond(withSuccess(body("\"\""), MediaType.APPLICATION_JSON));

		client.areaBasedList(new TourAreaQuery("51", null, "NA", null, 50));

		server.verify();
	}

	@Test
	void 항목을_읽고_좌표가_없는_항목은_버린다() {
		HttpTourApiClient client = client(PROPERTIES);
		server.expect(anything()).andRespond(withSuccess(body("""
			{"item":[
			 {"contentid":"126508","title":"경포해변","addr1":"강원특별자치도 강릉시 창해로 514","addr2":"(안현동)",
			  "mapx":"128.9087","mapy":"37.8055","firstimage":"http://img/1.jpg","lclsSystm1":"NA","lclsSystm2":"NA04",
			  "lclsSystm3":"NA040100"},
			 {"contentid":"999","title":"좌표 없음","mapx":"0","mapy":"0"},
			 {"contentid":"777","title":"주소 없음","mapx":"128.9","mapy":"37.7","firstimage":""}
			]}"""), MediaType.APPLICATION_JSON));

		List<TourPlace> places = client.areaBasedList(CAFE_IN_GANGNEUNG);

		assertThat(places).containsExactly(
			new TourPlace("126508", "경포해변", "강원특별자치도 강릉시 창해로 514 (안현동)", new BigDecimal("37.8055"),
				new BigDecimal("128.9087"), "http://img/1.jpg", "NA", "NA04", "NA040100"),
			new TourPlace("777", "주소 없음", null, new BigDecimal("37.7"), new BigDecimal("128.9"), null, null, null, null));
	}

	@Test
	void 한_건이면_객체로_와도_읽는다() {
		HttpTourApiClient client = client(PROPERTIES);
		server.expect(anything()).andRespond(withSuccess(body("""
			{"item":{"contentid":"1","title":"한 곳","mapx":"128.9","mapy":"37.7"}}"""), MediaType.APPLICATION_JSON));

		assertThat(client.areaBasedList(CAFE_IN_GANGNEUNG)).extracting(TourPlace::contentId).containsExactly("1");
	}

	@Test
	void 결과가_없으면_빈_목록() {
		HttpTourApiClient client = client(PROPERTIES);
		server.expect(anything()).andRespond(withSuccess(body("\"\""), MediaType.APPLICATION_JSON));

		assertThat(client.areaBasedList(CAFE_IN_GANGNEUNG)).isEmpty();
	}

	@Test
	void 오류_코드_응답은_실패() {
		HttpTourApiClient client = client(PROPERTIES);
		server.expect(anything()).andRespond(withSuccess("""
			{"response":{"header":{"resultCode":"10","resultMsg":"INVALID_REQUEST_PARAMETER_ERROR"}}}
			""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.areaBasedList(CAFE_IN_GANGNEUNG)).isInstanceOf(TourApiException.class)
			.hasMessageContaining("10");
	}

	@Test
	void HTTP_오류와_JSON_이_아닌_응답은_실패하고_인증키를_메시지에_넣지_않는다() {
		HttpTourApiClient client = client(PROPERTIES);
		server.expect(anything()).andRespond(withServerError());
		assertThatThrownBy(() -> client.areaBasedList(CAFE_IN_GANGNEUNG)).isInstanceOf(TourApiException.class)
			.hasMessageContaining("HTTP 500").hasMessageNotContaining(KEY);

		HttpTourApiClient xmlClient = client(PROPERTIES);
		server.expect(anything()).andRespond(withSuccess(
			"<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader>"
				+ "</OpenAPI_ServiceResponse>", MediaType.TEXT_XML));
		assertThatThrownBy(() -> xmlClient.areaBasedList(CAFE_IN_GANGNEUNG)).isInstanceOf(TourApiException.class)
			.hasMessageNotContaining(KEY);
	}

	@Test
	void 인증키가_없으면_부르지_않고_실패() {
		HttpTourApiClient client = client(new TourApiProperties("", "https://tour.test/KorService2", "Yeogirok",
			Duration.ofSeconds(2), Duration.ofSeconds(3)));
		server.expect(never(), anything());

		assertThatThrownBy(() -> client.areaBasedList(CAFE_IN_GANGNEUNG)).isInstanceOf(TourApiException.class);
		server.verify();
	}
}
