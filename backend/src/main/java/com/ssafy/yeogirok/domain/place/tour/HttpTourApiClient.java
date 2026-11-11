package com.ssafy.yeogirok.domain.place.tour;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * TourAPI(KorService2) HTTP 호출. 지금은 지역 기반 조회(areaBasedList2)만 쓴다.
 *
 * <ul>
 *   <li>인증키는 URI 변수로 넘겨 값 전체를 인코딩한다. Decoding 키의 {@code +}·{@code /}·{@code =} 가 그대로 나가면
 *       서버가 다른 키로 읽는다.</li>
 *   <li>정렬은 C(수정일순)다. Q(이미지 있는 항목만)를 쓰면 사진 없는 장소(작은 식당 등)가 아예 빠져 쓰지 않는다.</li>
 *   <li>결과가 없으면 items 가 빈 문자열로 오고, 키 오류 등은 JSON 이 아닌 응답이 올 수 있다. 둘 다 여기서 처리한다.</li>
 *   <li>요청 주소에 인증키가 들어 있어 예외 메시지·URI 를 로그에 남기지 않는다.</li>
 * </ul>
 */
@Slf4j
@Component
public class HttpTourApiClient implements TourApiClient {

	static final String AREA_BASED_LIST_PATH = "/areaBasedList2";
	private static final String SUCCESS_CODE = "0000";
	private static final String ARRANGE_MODIFIED = "C";

	private final RestClient restClient;
	private final TourApiProperties properties;

	@Autowired
	public HttpTourApiClient(TourApiProperties properties) {
		this(restClientBuilder(properties), properties);
	}

	/** 테스트는 {@link #restClientBuilder} 로 만든 빌더에 가짜 서버를 묶어 넘긴다. */
	HttpTourApiClient(RestClient.Builder restClientBuilder, TourApiProperties properties) {
		this.restClient = restClientBuilder.build();
		this.properties = properties;
		if (!properties.hasServiceKey()) {
			log.warn("TOUR_API_KEY 가 없어 TourAPI 를 부르지 않습니다. 장소 추천(API-034)은 C014 로 실패합니다.");
		}
	}

	/** 기준 주소·제한 시간과, URI 변수 값을 모두 인코딩하는 설정(TEMPLATE_AND_VALUES)을 담은 빌더. */
	static RestClient.Builder restClientBuilder(TourApiProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(properties.baseUrl());
		uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);
		return RestClient.builder().uriBuilderFactory(uriBuilderFactory).requestFactory(requestFactory);
	}

	@Override
	public List<TourPlace> areaBasedList(TourAreaQuery query) {
		if (!properties.hasServiceKey()) {
			throw new TourApiException("TourAPI 인증키가 없습니다.");
		}
		JsonNode response = call(query);
		JsonNode header = path(response, "response", "header");
		String resultCode = text(header, "resultCode");
		if (!SUCCESS_CODE.equals(resultCode)) {
			throw new TourApiException("TourAPI 오류 응답: " + resultCode + " " + text(header, "resultMsg"));
		}
		JsonNode items = path(response, "response", "body", "items", "item");
		List<TourPlace> places = new ArrayList<>();
		if (items == null) {
			return places;
		}
		// 한 건이면 배열이 아닌 객체로 오는 경우가 있어 둘 다 받는다.
		if (items.isArray()) {
			for (JsonNode item : items) {
				addIfLocated(places, item);
			}
		} else if (items.isObject()) {
			addIfLocated(places, items);
		}
		return places;
	}

	private JsonNode call(TourAreaQuery query) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("serviceKey", properties.serviceKey());
		try {
			JsonNode body = restClient.get()
				.uri(uri -> {
					uri.path(AREA_BASED_LIST_PATH)
						.queryParam("serviceKey", "{serviceKey}")
						.queryParam("MobileOS", "ETC")
						.queryParam("MobileApp", properties.mobileApp())
						.queryParam("_type", "json")
						.queryParam("arrange", ARRANGE_MODIFIED)
						.queryParam("numOfRows", query.numOfRows())
						.queryParam("pageNo", 1)
						.queryParam("lDongRegnCd", query.lDongRegnCd());
					optionalParam(uri, "lDongSignguCd", query.lDongSignguCd());
					optionalParam(uri, "lclsSystm1", query.lclsSystm1());
					optionalParam(uri, "lclsSystm2", query.lclsSystm2());
					return uri.build(variables);
				})
				.retrieve()
				.body(JsonNode.class);
			if (body == null) {
				throw new TourApiException("TourAPI 응답 본문이 없습니다.");
			}
			return body;
		} catch (TourApiException e) {
			throw e;
		} catch (RuntimeException e) {
			// 예외 메시지에 요청 주소(인증키)가 들어갈 수 있어 종류·상태 코드만 남긴다.
			String reason = e instanceof RestClientResponseException responseException
				? "HTTP " + responseException.getStatusCode().value()
				: e.getClass().getSimpleName();
			throw new TourApiException("TourAPI 호출 실패: " + reason);
		}
	}

	private static void optionalParam(UriBuilder uri, String name, String value) {
		if (value != null && !value.isBlank()) {
			uri.queryParam(name, value);
		}
	}

	private static void addIfLocated(List<TourPlace> places, JsonNode item) {
		String contentId = text(item, "contentid");
		String title = text(item, "title");
		BigDecimal latitude = decimal(text(item, "mapy"));
		BigDecimal longitude = decimal(text(item, "mapx"));
		if (contentId == null || title == null || latitude == null || longitude == null) {
			return;
		}
		places.add(new TourPlace(contentId, title, address(text(item, "addr1"), text(item, "addr2")),
			latitude, longitude, text(item, "firstimage"), text(item, "lclsSystm1"), text(item, "lclsSystm2"),
			text(item, "lclsSystm3")));
	}

	private static String address(String addr1, String addr2) {
		if (addr1 == null) {
			return null;
		}
		return addr2 == null ? addr1 : addr1 + " " + addr2;
	}

	/** 숫자로 읽을 수 없거나 0(좌표 미입력)이면 null. */
	private static BigDecimal decimal(String value) {
		if (value == null) {
			return null;
		}
		try {
			BigDecimal parsed = new BigDecimal(value);
			return parsed.signum() == 0 ? null : parsed;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static JsonNode path(JsonNode node, String... fields) {
		JsonNode current = node;
		for (String field : fields) {
			if (current == null || !current.isObject()) {
				return null;
			}
			current = current.get(field);
		}
		return current;
	}

	/** 비어 있지 않은 값. 숫자로 온 값도 문자열로 바꾼다. 없거나 빈 문자열이면 null. */
	private static String text(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.isString() ? value.stringValue() : value.isNumber() ? value.asString() : null;
		return text == null || text.isBlank() ? null : text.strip();
	}
}
