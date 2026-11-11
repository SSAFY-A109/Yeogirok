package com.ssafy.yeogirok.domain.place.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.recommendation.service.PlaceRecommendationService;

import tools.jackson.databind.JsonNode;

/**
 * 실제 TourAPI 응답이 우리 코드가 기대하는 형식과 맞는지 확인하는 수동 테스트. 가짜 응답으로 만든 {@link HttpTourApiClientTest} 가
 * 볼 수 없는 것(실제 필드 이름·모양, 신분류체계 코드, 매핑표에 없는 코드)을 본다.
 *
 * <p><b>환경 변수 TOUR_API_KEY 가 있을 때만 돈다</b>(없으면 건너뛴다). CI·평소 테스트는 실제 API 를 부르지 않는다.
 * <pre>
 * TOUR_API_KEY=Decoding키 ./gradlew test --tests '*TourApiLiveTest' -i
 * TOUR_API_KEY=Decoding키 TOUR_API_REGION=11680 ./gradlew test --tests '*TourApiLiveTest' -i   # 다른 지역(서울 강남구)
 * </pre>
 * 출력(-i)이나 build/reports/tests/test/index.html 의 Standard output 에서 결과를 본다. 호출 수는 10번이다(원본 1 + 카페 1 + 모든 카테고리 8, 일일 한도 참고).
 */
class TourApiLiveTest {

	/** 기본은 강릉시. 시/군/구 법정동 코드 5자리. */
	static final String DEFAULT_REGION = "51150";

	static String key;
	static String region;
	static TourApiProperties properties;

	@BeforeAll
	static void requireKey() {
		key = System.getenv("TOUR_API_KEY");
		assumeTrue(key != null && !key.isBlank(), "TOUR_API_KEY 가 없어 실제 TourAPI 테스트를 건너뜁니다.");
		String regionEnv = System.getenv("TOUR_API_REGION");
		region = regionEnv == null || regionEnv.isBlank() ? DEFAULT_REGION : regionEnv.strip();
		properties = new TourApiProperties(key, "https://apis.data.go.kr/B551011/KorService2", "Yeogirok",
			Duration.ofSeconds(5), Duration.ofSeconds(10));
	}

	static HttpTourApiClient client() {
		return new HttpTourApiClient(HttpTourApiClient.restClientBuilder(properties), properties);
	}

	/** 우리 코드를 거치지 않은 원본 응답. 코드가 읽는 경로·필드가 실제로 있는지 본다. */
	@Test
	void 원본_응답에_코드가_읽는_경로와_필드가_있다() {
		JsonNode body = HttpTourApiClient.restClientBuilder(properties).build().get()
			.uri(uri -> uri.path(HttpTourApiClient.AREA_BASED_LIST_PATH)
				.queryParam("serviceKey", "{serviceKey}")
				.queryParam("MobileOS", "ETC").queryParam("MobileApp", "Yeogirok").queryParam("_type", "json")
				.queryParam("arrange", "C").queryParam("numOfRows", 3).queryParam("pageNo", 1)
				.queryParam("lDongRegnCd", region.substring(0, 2)).queryParam("lDongSignguCd", region.substring(2))
				.build(Map.of("serviceKey", key)))
			.retrieve().body(JsonNode.class);

		JsonNode header = body.path("response").path("header");
		System.out.println("[원본] header = " + header);
		assertThat(header.path("resultCode").asString()).as("resultCode(키·파라미터 확인)").isEqualTo("0000");

		JsonNode items = body.path("response").path("body").path("items");
		System.out.println("[원본] totalCount = " + body.path("response").path("body").path("totalCount"));
		JsonNode item = items.path("item");
		assertThat(item.isArray() || item.isObject()).as("items.item 이 배열 또는 객체").isTrue();
		JsonNode first = item.isArray() ? item.get(0) : item;
		List<String> fields = new ArrayList<>();
		Iterator<String> names = first.propertyNames().iterator();
		names.forEachRemaining(fields::add);
		System.out.println("[원본] 첫 항목 필드 = " + fields);
		System.out.println("[원본] 첫 항목 = " + first);
		assertThat(fields).as("코드가 읽는 필드")
			.contains("contentid", "title", "addr1", "mapx", "mapy", "firstimage", "lclsSystm1", "lclsSystm2",
				"lclsSystm3");
	}

	/** 우리 클라이언트로 읽은 값이 비지 않고, 좌표·분류가 상식적인지 본다. */
	@Test
	void 클라이언트가_읽은_장소의_값이_채워져_있다() {
		List<TourPlace> places = client().areaBasedList(
			new TourAreaQuery(region.substring(0, 2), region.substring(2), "FD", "FD05", 20));

		System.out.println("[클라이언트] 카페(FD/FD05) " + places.size() + "개");
		places.stream().limit(5).forEach(place -> System.out.println("  " + place));
		assertThat(places).as("이 지역 카페 결과(0개면 지역 코드나 분류 조건을 의심)").isNotEmpty();
		assertThat(places).allSatisfy(place -> {
			assertThat(place.contentId()).isNotBlank();
			assertThat(place.title()).isNotBlank();
			// 한국 범위. 위도·경도가 바뀌어 읽히면 여기서 걸린다.
			assertThat(place.latitude()).isBetween(new BigDecimal("33"), new BigDecimal("39"));
			assertThat(place.longitude()).isBetween(new BigDecimal("124"), new BigDecimal("132"));
			assertThat(place.lclsSystm2()).isEqualTo("FD05");
			assertThat(place.category()).isEqualTo(TravelCategory.CAFE);
		});
	}

	/** 모든 카테고리 조회 계획대로 불러 매핑 결과를 센다. 매핑표에 없는 분류 코드가 나오면 출력한다. */
	@Test
	void 카테고리별_후보_수와_매핑되지_않은_코드() {
		HttpTourApiClient client = client();
		Map<String, Integer> perCategory = new LinkedHashMap<>();
		Map<String, Integer> unmapped = new TreeMap<>();
		for (TravelCategory category : TravelCategory.values()) {
			perCategory.put(category.name(), 0);
		}
		List<TourAreaQuery> queries = PlaceRecommendationService.planQueries(List.of(TravelCategory.values()), region,
			100, 12);
		for (TourAreaQuery query : queries) {
			List<TourPlace> places = client.areaBasedList(query);
			System.out.println("[계획] " + query.lclsSystm1() + (query.lclsSystm2() == null ? "" : "/" + query.lclsSystm2())
				+ " → " + places.size() + "개");
			for (TourPlace place : places) {
				TravelCategory category = place.category();
				if (category == null) {
					unmapped.merge(place.lclsSystm2() + "/" + place.lclsSystm3(), 1, Integer::sum);
				} else {
					perCategory.merge(category.name(), 1, Integer::sum);
				}
			}
		}
		System.out.println("[매핑] 카테고리별 후보 수 = " + perCategory);
		System.out.println("[매핑] 카테고리 없음으로 빠진 코드(중분류/소분류 = 개수) = " + unmapped);
		assertThat(queries).hasSizeLessThanOrEqualTo(12);
	}
}
