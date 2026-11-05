package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 카카오 로컬 API 역지오코딩. 법정동 코드·이름은 coord2regioncode(region_type B), 주소·건물 이름은 coord2address.
 * 발자취는 {@link ReverseGeocoder}(두 호출), 후보 장소 직접 추가는 {@link LegalDongResolver}(법정동만)로 쓴다.
 * 두 호출은 따로 실패할 수 있고 실패한 쪽 값만 null 이 된다. x 는 경도, y 는 위도다. 키·좌표는 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class KakaoReverseGeocoder implements ReverseGeocoder, LegalDongResolver {

	private static final String REGION_PATH = "/v2/local/geo/coord2regioncode.json";
	private static final String ADDRESS_PATH = "/v2/local/geo/coord2address.json";

	private final RestClient restClient;
	private final KakaoLocalProperties properties;

	@Autowired
	public KakaoReverseGeocoder(KakaoLocalProperties properties) {
		this(createRestClient(properties), properties);
	}

	KakaoReverseGeocoder(RestClient restClient, KakaoLocalProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
		if (!properties.hasRestApiKey()) {
			log.warn("KAKAO_REST_API_KEY 가 없어 역지오코딩을 하지 않습니다. 발자취의 주소·위치명·법정동 코드는 null 로 저장됩니다.");
		}
	}

	private static RestClient createRestClient(KakaoLocalProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

	/** 주소는 도로명 → 지번, 위치명은 건물 이름 → 법정리 → 법정동 순으로 있는 값을 쓴다. */
	@Override
	public ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
		if (!properties.hasRestApiKey()) {
			return ReverseGeocodeResult.EMPTY;
		}
		JsonNode region = firstDocument(REGION_PATH, latitude, longitude,
			document -> "B".equals(text(document, "region_type")));
		JsonNode address = firstDocument(ADDRESS_PATH, latitude, longitude, document -> true);
		JsonNode road = address == null ? null : address.get("road_address");
		JsonNode lot = address == null ? null : address.get("address");

		return new ReverseGeocodeResult(
			text(region, "code"),
			firstNonNull(text(road, "address_name"), text(lot, "address_name")),
			firstNonNull(text(road, "building_name"), text(region, "region_4depth_name"),
				text(region, "region_3depth_name")));
	}

	/** coord2regioncode 만 부른다(주소 조회 없음). */
	@Override
	public LegalDong resolve(BigDecimal latitude, BigDecimal longitude) {
		if (!properties.hasRestApiKey()) {
			return LegalDong.EMPTY;
		}
		JsonNode region = firstDocument(REGION_PATH, latitude, longitude,
			document -> "B".equals(text(document, "region_type")));
		return new LegalDong(text(region, "code"), text(region, "region_3depth_name"));
	}

	/** 조건에 맞는 첫 documents 항목. 결과가 없거나 호출이 실패하면 null. */
	private JsonNode firstDocument(String path, BigDecimal latitude, BigDecimal longitude, Predicate<JsonNode> filter) {
		try {
			JsonNode response = restClient.get()
				.uri(uri -> uri.path(path)
					.queryParam("x", longitude.toPlainString())
					.queryParam("y", latitude.toPlainString())
					.build())
				.header("Authorization", "KakaoAK " + properties.restApiKey())
				.retrieve()
				.body(JsonNode.class);
			JsonNode documents = response == null ? null : response.get("documents");
			for (int i = 0; documents != null && i < documents.size(); i++) {
				if (filter.test(documents.get(i))) {
					return documents.get(i);
				}
			}
			return null;
		} catch (RuntimeException e) {
			// 예외 메시지에는 요청 주소(좌표)가 들어갈 수 있어 종류·상태 코드만 남긴다.
			String reason = e instanceof RestClientResponseException responseException
				? "HTTP " + responseException.getStatusCode().value()
				: e.getClass().getSimpleName();
			log.warn("카카오 {} 호출에 실패해 해당 값을 null 로 둡니다: {}", path, reason);
			return null;
		}
	}

	/** 비어 있지 않은 문자열 값. 노드·필드가 없거나 null·빈 문자열이면 null. */
	private static String text(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value != null && value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
	}

	private static String firstNonNull(String... values) {
		for (String value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}
}
