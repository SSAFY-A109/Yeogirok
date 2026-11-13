package com.ssafy.yeogirok.global.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch.Outcome;
import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch.Place;

/** 카카오 키워드 검색 응답 해석(응답 모양은 카카오 로컬 API 문서 기준). 실제 카카오 호출은 이 테스트 범위 밖이다(미확인). */
class KakaoKeywordPlaceSearchTest {

	static final String PATH = "https://dapi.kakao.com/v2/local/search/keyword.json";

	MockRestServiceServer server;

	KakaoKeywordPlaceSearch search(String key) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		return new KakaoKeywordPlaceSearch(builder, new KakaoLocalProperties(key, "https://dapi.kakao.com",
			Duration.ofSeconds(2), Duration.ofSeconds(3)));
	}

	@Test
	void 검색어와_키로_부르고_이름_좌표가_있는_문서만_순서대로_준다() {
		KakaoKeywordPlaceSearch search = search("test-key");
		server.expect(requestTo(Matchers.startsWith(PATH))).andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("query", Matchers.anything()))
			.andExpect(queryParam("size", "15"))
			.andExpect(header("Authorization", "KakaoAK test-key"))
			.andRespond(withSuccess("""
				{"meta":{"total_count":3},"documents":[
				  {"place_name":"해운대 커피 본점","x":"129.1603","y":"35.1631",
				   "road_address_name":"부산 해운대구 해운대로 570","address_name":"부산 해운대구 우동 1411"},
				  {"place_name":"좌표 없음","road_address_name":"부산 해운대구 해운대로 1"},
				  {"place_name":"해운대 커피 2호점","x":"129.17","y":"35.16","road_address_name":"",
				   "address_name":"부산 해운대구 중동 1"}
				]}
				""", MediaType.APPLICATION_JSON));

		PlaceKeywordSearch.Result result = search.search("부산 해운대구 해운대로 해운대 커피");

		assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
		assertThat(result.places()).containsExactly(
			new Place("해운대 커피 본점", new BigDecimal("35.1631"), new BigDecimal("129.1603"),
				"부산 해운대구 해운대로 570", "부산 해운대구 우동 1411"),
			new Place("해운대 커피 2호점", new BigDecimal("35.16"), new BigDecimal("129.17"), null,
				"부산 해운대구 중동 1"));
		server.verify();
	}

	@Test
	void 결과가_없으면_NOT_FOUND() {
		KakaoKeywordPlaceSearch search = search("test-key");
		server.expect(requestTo(Matchers.startsWith(PATH)))
			.andRespond(withSuccess("{\"meta\":{\"total_count\":0},\"documents\":[]}", MediaType.APPLICATION_JSON));

		assertThat(search.search("q")).isEqualTo(PlaceKeywordSearch.Result.NOT_FOUND);
	}

	@Test
	void 서버_오류와_시간_초과는_FAILED_이고_원인에_검색어를_넣지_않는다() {
		KakaoKeywordPlaceSearch search = search("test-key");
		server.expect(requestTo(Matchers.startsWith(PATH))).andRespond(withServerError());
		server.expect(requestTo(Matchers.startsWith(PATH))).andRespond(withException(new SocketTimeoutException("q")));

		assertThat(search.search("부산 해운대구 해운대로 가게")).isEqualTo(PlaceKeywordSearch.Result.failed("HTTP 500"));
		PlaceKeywordSearch.Result timeout = search.search("부산 해운대구 해운대로 가게");
		assertThat(timeout.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(timeout.failure()).doesNotContain("해운대");
	}

	@Test
	void 키가_없으면_부르지_않고_NO_KEY() {
		KakaoKeywordPlaceSearch search = search("");

		assertThat(search.search("q")).isEqualTo(PlaceKeywordSearch.Result.NO_KEY);
		assertThat(search.search("q").places()).isEqualTo(List.of());
		server.verify();
	}
}
