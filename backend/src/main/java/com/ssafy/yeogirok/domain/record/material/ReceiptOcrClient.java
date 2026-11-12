package com.ssafy.yeogirok.domain.record.material;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 영수증 OCR 서버 호출(ai/dev {@code ai/ocr/app.py}). {@code POST /ocr/receipt}, multipart 필드 {@code file}, 10MB 이하.
 * 응답에서 상호·날짜·시각과 v3 주소({@code address}·{@code addressComplete}, 명세 {@code specs/draft-merge.md} 1절)를 쓴다.
 * 주소 칸이 없는 지금 응답도 그대로 받고(null·false), status 는 읽지 않는다.
 *
 * <p>실패는 예외로 알린다. 400(읽을 수 없는 이미지) → M003, 413(10MB 초과) → M001 은 다시 시도해도 같아 업무 오류로,
 * 5xx·시간 초과·연결 실패·주소 없음은 일시 오류로 던진다(작업이 C014·다시 시도 가능으로 바꾼다). 로그에는 상태 코드·예외 종류만 남긴다.
 */
@Slf4j
@Component
public class ReceiptOcrClient {

	private static final String PATH = "/ocr/receipt";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private final RestClient restClient;
	private final OcrProperties properties;

	@Autowired
	public ReceiptOcrClient(OcrProperties properties) {
		this(createBuilder(properties), properties);
	}

	ReceiptOcrClient(RestClient.Builder builder, OcrProperties properties) {
		this.restClient = properties.hasBaseUrl() ? builder.baseUrl(properties.baseUrl()).build() : builder.build();
		this.properties = properties;
		if (!properties.hasBaseUrl()) {
			log.warn("OCR_BASE_URL 이 없어 영수증 OCR 작업이 바로 실패합니다(사용자는 API-091 로 직접 입력할 수 있습니다).");
		}
	}

	private static RestClient.Builder createBuilder(OcrProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().requestFactory(requestFactory);
	}

	public OcrResult recognize(byte[] image) {
		if (!properties.hasBaseUrl()) {
			throw new IllegalStateException("OCR_BASE_URL 이 설정되지 않았습니다.");
		}
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(image) {
			@Override
			public String getFilename() {
				return "receipt";
			}
		});
		JsonNode response;
		try {
			response = restClient.post().uri(PATH).contentType(MediaType.MULTIPART_FORM_DATA).body(body)
				.retrieve().body(JsonNode.class);
		} catch (RestClientResponseException e) {
			log.warn("OCR 서버가 실패를 돌려줬습니다. status={}", e.getStatusCode().value());
			throw switch (e.getStatusCode().value()) {
				case 400 -> new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED, "영수증 이미지를 읽을 수 없습니다.");
				case 413 -> new BusinessException(ExceptionType.FILE_TOO_LARGE, "OCR 은 10MB 이하 이미지만 받습니다.");
				default -> new IllegalStateException("OCR 서버 오류 status=" + e.getStatusCode().value());
			};
		}
		return toResult(response);
	}

	static OcrResult toResult(JsonNode response) {
		if (response == null) {
			return new OcrResult(null, null, null);
		}
		JsonNode complete = response.get("addressComplete");
		return new OcrResult(text(response.get("placeName")), date(text(response.get("date"))),
			time(text(response.get("time"))), text(response.get("address")),
			complete != null && complete.isBoolean() && complete.booleanValue());
	}

	private static String text(JsonNode value) {
		if (value == null || !value.isString() || value.stringValue().isBlank()) {
			return null;
		}
		return value.stringValue().strip();
	}

	private static LocalDate date(String value) {
		try {
			return value == null ? null : LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static LocalTime time(String value) {
		try {
			return value == null ? null : LocalTime.parse(value, TIME);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
