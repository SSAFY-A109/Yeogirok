package com.ssafy.yeogirok.global.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 카카오 응답을 해석하는 규칙. 실제 카카오를 부르지 않고 응답을 흉내 낸다(응답 모양은 카카오 로컬 API 문서 기준).
 * 실제 키로 부르는 확인은 이 테스트 범위 밖이다.
 */
class KakaoReverseGeocoderTest {

	static final BigDecimal LAT = new BigDecimal("37.5000000");
	static final BigDecimal LNG = new BigDecimal("127.0300000");
	static final String REGION_PATH = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json";
	static final String ADDRESS_PATH = "https://dapi.kakao.com/v2/local/geo/coord2address.json";

	static final String REGION_JSON = """
		{"meta":{"total_count":2},"documents":[
		  {"region_type":"H","code":"1168064000","address_name":"서울특별시 강남구 역삼1동",
		   "region_1depth_name":"서울특별시","region_2depth_name":"강남구","region_3depth_name":"역삼1동",
		   "region_4depth_name":"","x":127.03,"y":37.5},
		  {"region_type":"B","code":"1168010100","address_name":"서울특별시 강남구 역삼동",
		   "region_1depth_name":"서울특별시","region_2depth_name":"강남구","region_3depth_name":"역삼동",
		   "region_4depth_name":"","x":127.03,"y":37.5}
		]}
		""";

	static final String ADDRESS_JSON = """
		{"meta":{"total_count":1},"documents":[
		  {"road_address":{"address_name":"서울특별시 강남구 테헤란로 152","region_1depth_name":"서울",
		    "road_name":"테헤란로","building_name":"강남파이낸스센터","zone_no":"06236"},
		   "address":{"address_name":"서울 강남구 역삼동 737","region_3depth_name":"역삼동","mountain_yn":"N"}}
		]}
		""";

	MockRestServiceServer server;
	KakaoReverseGeocoder geocoder;

	@BeforeEach
	void setUp() {
		geocoder = geocoderWithKey("test-key");
	}

	@Test
	void 법정동_코드는_B_유형_주소는_도로명_위치명은_건물_이름을_쓴다() {
		expectRegion().andRespond(withSuccess(REGION_JSON, MediaType.APPLICATION_JSON));
		expectAddress().andRespond(withSuccess(ADDRESS_JSON, MediaType.APPLICATION_JSON));

		ReverseGeocodeResult result = geocoder.reverseGeocode(LAT, LNG);

		assertThat(result).isEqualTo(
			new ReverseGeocodeResult("1168010100", "서울특별시 강남구 테헤란로 152", "강남파이낸스센터"));
		server.verify();
	}

	@Test
	void 도로명_주소가_없으면_지번_주소와_법정동_이름을_쓴다() {
		expectRegion().andRespond(withSuccess(REGION_JSON, MediaType.APPLICATION_JSON));
		expectAddress().andRespond(withSuccess("""
			{"meta":{"total_count":1},"documents":[
			  {"road_address":null,"address":{"address_name":"서울 강남구 역삼동 737"}}
			]}
			""", MediaType.APPLICATION_JSON));

		assertThat(geocoder.reverseGeocode(LAT, LNG))
			.isEqualTo(new ReverseGeocodeResult("1168010100", "서울 강남구 역삼동 737", "역삼동"));
	}

	@Test
	void 읍면_지역은_법정리_이름을_위치명으로_쓴다() {
		expectRegion().andRespond(withSuccess("""
			{"meta":{"total_count":1},"documents":[
			  {"region_type":"B","code":"4182025021","region_3depth_name":"청평면","region_4depth_name":"대성리"}
			]}
			""", MediaType.APPLICATION_JSON));
		expectAddress().andRespond(withSuccess("{\"meta\":{\"total_count\":0},\"documents\":[]}",
			MediaType.APPLICATION_JSON));

		assertThat(geocoder.reverseGeocode(LAT, LNG))
			.isEqualTo(new ReverseGeocodeResult("4182025021", null, "대성리"));
	}

	@Test
	void 결과가_없으면_모두_null() {
		expectRegion().andRespond(withSuccess("{\"meta\":{\"total_count\":0},\"documents\":[]}",
			MediaType.APPLICATION_JSON));
		expectAddress().andRespond(withSuccess("{\"meta\":{\"total_count\":0},\"documents\":[]}",
			MediaType.APPLICATION_JSON));

		assertThat(geocoder.reverseGeocode(LAT, LNG)).isEqualTo(ReverseGeocodeResult.EMPTY);
	}

	@Test
	void 쿼터_초과_429와_서버_오류는_예외_없이_실패한_쪽만_null() {
		expectRegion().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		expectAddress().andRespond(withSuccess(ADDRESS_JSON, MediaType.APPLICATION_JSON));

		assertThat(geocoder.reverseGeocode(LAT, LNG))
			.isEqualTo(new ReverseGeocodeResult(null, "서울특별시 강남구 테헤란로 152", "강남파이낸스센터"));

		geocoder = geocoderWithKey("test-key");
		expectRegion().andRespond(withSuccess(REGION_JSON, MediaType.APPLICATION_JSON));
		expectAddress().andRespond(withServerError());

		assertThat(geocoder.reverseGeocode(LAT, LNG))
			.isEqualTo(new ReverseGeocodeResult("1168010100", null, "역삼동"));
	}

	@Test
	void 키가_없으면_호출하지_않고_모두_null() {
		geocoder = geocoderWithKey("");

		assertThat(geocoder.reverseGeocode(LAT, LNG)).isEqualTo(ReverseGeocodeResult.EMPTY);
		server.verify();  // 기대한 요청이 없으니 요청이 하나라도 가면 실패한다
	}

	@Test
	void 설정의_toString에_키가_나오지_않는다() {
		assertThat(properties("secret-key-value").toString()).doesNotContain("secret-key-value");
	}

	private org.springframework.test.web.client.ResponseActions expectRegion() {
		return server.expect(requestTo(Matchers.startsWith(REGION_PATH)))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("x", "127.0300000"))   // x 는 경도
			.andExpect(queryParam("y", "37.5000000"))    // y 는 위도
			.andExpect(header("Authorization", "KakaoAK test-key"));
	}

	private org.springframework.test.web.client.ResponseActions expectAddress() {
		return server.expect(requestTo(Matchers.startsWith(ADDRESS_PATH)))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("x", "127.0300000"))
			.andExpect(queryParam("y", "37.5000000"))
			.andExpect(header("Authorization", "KakaoAK test-key"));
	}

	private KakaoReverseGeocoder geocoderWithKey(String key) {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
		server = MockRestServiceServer.bindTo(builder).build();
		return new KakaoReverseGeocoder(builder.build(), properties(key));
	}

	private static KakaoLocalProperties properties(String key) {
		return new KakaoLocalProperties(key, "https://dapi.kakao.com", Duration.ofSeconds(2), Duration.ofSeconds(3));
	}
}
