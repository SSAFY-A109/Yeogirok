package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 카카오 로컬 API 키워드 장소 검색({@code /v2/local/search/keyword.json}). 키·제한 시간은 {@link KakaoLocalProperties} 를 같이 쓴다.
 * 키가 없으면 부르지 않는다. 검색어(주소·상호)·키·좌표는 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class KakaoKeywordPlaceSearch implements PlaceKeywordSearch {

	private static final String PATH = "/v2/local/search/keyword.json";
	/** 카카오 한 쪽 최대 개수. 결과를 검증해 고르므로 넉넉히 받는다. */
	private static final int SIZE = 15;

	private final RestClient restClient;
	private final KakaoLocalProperties properties;

	@Autowired
	public KakaoKeywordPlaceSearch(KakaoLocalProperties properties) {
		this(KakaoLocalClients.builder(properties), properties);
	}

	KakaoKeywordPlaceSearch(RestClient.Builder builder, KakaoLocalProperties properties) {
		this.restClient = builder.baseUrl(properties.baseUrl()).build();
		this.properties = properties;
		if (!properties.hasRestApiKey()) {
			log.warn("KAKAO_REST_API_KEY 가 없어 영수증 장소 후보를 찾지 않습니다(API-091 에서 장소를 직접 검색해 고릅니다).");
		}
	}

	@Override
	public Result search(String query) {
		if (!properties.hasRestApiKey()) {
			return Result.NO_KEY;
		}
		try {
			JsonNode response = restClient.get()
				.uri(uri -> uri.path(PATH).queryParam("query", query).queryParam("size", SIZE).build())
				.header("Authorization", "KakaoAK " + properties.restApiKey())
				.retrieve()
				.body(JsonNode.class);
			JsonNode documents = response == null ? null : response.get("documents");
			List<Place> places = new ArrayList<>();
			for (int i = 0; documents != null && i < documents.size(); i++) {
				place(documents.get(i)).ifPresent(places::add);
			}
			return Result.found(places);
		} catch (RuntimeException e) {
			// 예외 메시지에는 요청 주소(검색어)가 들어갈 수 있어 종류·상태 코드만 돌려준다.
			return Result.failed(e instanceof RestClientResponseException responseException
				? "HTTP " + responseException.getStatusCode().value()
				: e.getClass().getSimpleName());
		}
	}

	/** 이름·좌표가 모두 있는 문서만. x 는 경도, y 는 위도. */
	private static Optional<Place> place(JsonNode document) {
		String name = text(document, "place_name");
		BigDecimal longitude = decimal(text(document, "x"));
		BigDecimal latitude = decimal(text(document, "y"));
		if (name == null || longitude == null || latitude == null) {
			return Optional.empty();
		}
		return Optional.of(new Place(name, latitude, longitude, text(document, "road_address_name"),
			text(document, "address_name")));
	}

	private static BigDecimal decimal(String value) {
		try {
			return value == null ? null : new BigDecimal(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value != null && value.isString() && !value.stringValue().isBlank() ? value.stringValue().strip() : null;
	}
}
