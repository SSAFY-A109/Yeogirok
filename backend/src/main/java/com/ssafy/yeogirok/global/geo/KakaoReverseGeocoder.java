package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 로컬 API 로 좌표를 법정동 코드·주소·위치명으로 바꾼다.
 *
 * <ul>
 *   <li>법정동·법정리 코드와 이름: {@code GET /v2/local/geo/coord2regioncode.json} 의 region_type {@code B}</li>
 *   <li>주소·건물 이름: {@code GET /v2/local/geo/coord2address.json}</li>
 * </ul>
 * 두 호출은 따로 실패할 수 있고, 실패한 쪽의 값만 null 이 된다. 실패(결과 없음, 4xx·5xx, 429 쿼터 초과, 시간 초과)는
 * 경고 로그만 남기고 저장을 막지 않는다. 카카오 API 의 x 는 경도, y 는 위도다.
 *
 * <p>요청 헤더(키)는 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class KakaoReverseGeocoder implements ReverseGeocoder {

	private static final String LEGAL_REGION_TYPE = "B";

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

	@Override
	public ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
		if (!properties.hasRestApiKey()) {
			return ReverseGeocodeResult.EMPTY;
		}
		Optional<RegionDocument> legalRegion = fetchLegalRegion(latitude, longitude);
		Optional<AddressDocument> address = fetchAddress(latitude, longitude);

		String roadBuildingName = address.map(AddressDocument::roadAddress).map(RoadAddress::buildingName)
			.filter(KakaoReverseGeocoder::hasText).orElse(null);
		String locationName = roadBuildingName != null ? roadBuildingName
			: legalRegion.map(RegionDocument::lowestName).orElse(null);
		String addressName = address.map(AddressDocument::displayAddress).orElse(null);
		String legalAreaCode = legalRegion.map(RegionDocument::code).filter(KakaoReverseGeocoder::hasText).orElse(null);
		return new ReverseGeocodeResult(legalAreaCode, addressName, locationName);
	}

	private Optional<RegionDocument> fetchLegalRegion(BigDecimal latitude, BigDecimal longitude) {
		return call("coord2regioncode", "/v2/local/geo/coord2regioncode.json", latitude, longitude, RegionResponse.class)
			.flatMap(response -> response.documents().stream()
				.filter(document -> LEGAL_REGION_TYPE.equals(document.regionType()))
				.findFirst());
	}

	private Optional<AddressDocument> fetchAddress(BigDecimal latitude, BigDecimal longitude) {
		return call("coord2address", "/v2/local/geo/coord2address.json", latitude, longitude, AddressResponse.class)
			.flatMap(response -> response.documents().stream().findFirst());
	}

	private <T extends KakaoResponse> Optional<T> call(String name, String path, BigDecimal latitude,
		BigDecimal longitude, Class<T> type) {
		try {
			T response = restClient.get()
				.uri(uri -> uri.path(path)
					.queryParam("x", longitude.toPlainString())
					.queryParam("y", latitude.toPlainString())
					.build())
				.header("Authorization", "KakaoAK " + properties.restApiKey())
				.retrieve()
				.body(type);
			if (response == null || response.documents() == null) {
				return Optional.empty();
			}
			return Optional.of(response);
		} catch (RuntimeException e) {
			// 좌표는 개인 위치라 로그에 남기지 않는다. 예외 메시지에는 요청 주소(좌표)가 들어갈 수 있어 종류·상태 코드만 남긴다.
			String reason = e instanceof RestClientResponseException responseException
				? "HTTP " + responseException.getStatusCode().value()
				: e.getClass().getSimpleName();
			log.warn("카카오 {} 호출에 실패해 해당 값을 null 로 둡니다: {}", name, reason);
			return Optional.empty();
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	interface KakaoResponse {
		List<?> documents();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RegionResponse(List<RegionDocument> documents) implements KakaoResponse {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RegionDocument(
		@JsonProperty("region_type") String regionType,
		String code,
		@JsonProperty("region_3depth_name") String region3DepthName,
		@JsonProperty("region_4depth_name") String region4DepthName
	) {

		/** 읍·면 지역은 법정리(4단계), 그 밖은 법정동(3단계) 이름. */
		String lowestName() {
			if (hasText(region4DepthName)) {
				return region4DepthName;
			}
			return hasText(region3DepthName) ? region3DepthName : null;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AddressResponse(List<AddressDocument> documents) implements KakaoResponse {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AddressDocument(
		@JsonProperty("road_address") RoadAddress roadAddress,
		@JsonProperty("address") LotAddress lotAddress
	) {

		String displayAddress() {
			if (roadAddress != null && hasText(roadAddress.addressName())) {
				return roadAddress.addressName();
			}
			return lotAddress != null && hasText(lotAddress.addressName()) ? lotAddress.addressName() : null;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RoadAddress(
		@JsonProperty("address_name") String addressName,
		@JsonProperty("building_name") String buildingName
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LotAddress(@JsonProperty("address_name") String addressName) {
	}
}
