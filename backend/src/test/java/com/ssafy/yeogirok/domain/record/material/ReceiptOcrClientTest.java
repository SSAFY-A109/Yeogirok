package com.ssafy.yeogirok.domain.record.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ssafy.yeogirok.global.exception.BusinessException;

/** OCR 서버 응답 해석과 실패 구분. 실제 OCR 서버는 부르지 않는다(응답 모양은 ai/dev ai/ocr/app.py 기준). */
class ReceiptOcrClientTest {

	static final String URL = "http://ocr.test/ocr/receipt";
	static final OcrProperties PROPERTIES = new OcrProperties("http://ocr.test", Duration.ofSeconds(2),
		Duration.ofSeconds(20), Duration.ofSeconds(30));

	MockRestServiceServer server;

	ReceiptOcrClient client(OcrProperties properties) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		return new ReceiptOcrClient(builder, properties);
	}

	@Test
	void 상호_날짜_시각과_v3_주소를_읽고_status_는_읽지_않는다() {
		ReceiptOcrClient client = client(PROPERTIES);
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
			.andRespond(withSuccess("""
				{"status":"SUCCESS","placeName":" 해운대 커피 ","date":"2026-11-08","time":"14:05",
				 "address":"부산 해운대구 ...","addressComplete":true}
				""", MediaType.APPLICATION_JSON));

		OcrResult result = client.recognize(new byte[] {1, 2, 3});

		assertThat(result).isEqualTo(new OcrResult("해운대 커피", LocalDate.of(2026, 11, 8), LocalTime.of(14, 5),
			"부산 해운대구 ...", true));
		server.verify();
	}

	/** 지금 운영 응답에는 주소 칸이 없다(v3 반영 전). 칸이 없거나 타입이 틀려도 null·false 로 계속한다. */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"placeName\":\"가게\"}|null|false",
		"{\"placeName\":\"가게\",\"address\":null,\"addressComplete\":false}|null|false",
		"{\"placeName\":\"가게\",\"address\":\"  \",\"addressComplete\":true}|null|false",
		"{\"placeName\":\"가게\",\"address\":3,\"addressComplete\":true}|null|false",
		"{\"placeName\":\"가게\",\"address\":\"서울 노원구 동일로\",\"addressComplete\":\"true\"}|서울 노원구 동일로|false",
		"{\"placeName\":\"가게\",\"address\":\"서울 노원구 동일로\",\"addressComplete\":false}|서울 노원구 동일로|false",
		"{\"placeName\":\"가게\",\"address\":\"서울 노원구\",\"addressComplete\":true}|서울 노원구|true"
	}, nullValues = "null")
	void 주소_칸이_없거나_타입이_틀리면_null_false(String body, String address, boolean complete) {
		ReceiptOcrClient client = client(PROPERTIES);
		server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		OcrResult result = client.recognize(new byte[] {1});

		assertThat(result.address()).isEqualTo(address);
		assertThat(result.addressComplete()).isEqualTo(complete);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"status\":\"FAILED\",\"placeName\":null,\"date\":null,\"time\":null}",
		"{\"placeName\":\"  \",\"date\":\"2026-13-01\",\"time\":\"25:00\"}",
		"{\"placeName\":3,\"date\":\"2026/11/08\",\"time\":\"14:05:00\"}",
		"{}"
	})
	void 비었거나_형식이_틀린_값은_null(String body) {
		ReceiptOcrClient client = client(PROPERTIES);
		server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		assertThat(client.recognize(new byte[] {1})).isEqualTo(new OcrResult(null, null, null));
	}

	@ParameterizedTest
	@CsvSource({"BAD_REQUEST, M003", "CONTENT_TOO_LARGE, M001"})
	void 다시_해도_같은_실패는_업무_오류(HttpStatus status, String code) {
		ReceiptOcrClient client = client(PROPERTIES);
		server.expect(requestTo(URL)).andRespond(withStatus(status));

		assertThatThrownBy(() -> client.recognize(new byte[] {1}))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType().getCode()).isEqualTo(code));
	}

	@Test
	void 서버_오류는_일시_오류() {
		ReceiptOcrClient client = client(PROPERTIES);
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> client.recognize(new byte[] {1}))
			.isInstanceOf(RuntimeException.class).isNotInstanceOf(BusinessException.class);
	}

	@Test
	void 시간_초과는_일시_오류() {
		ReceiptOcrClient client = client(PROPERTIES);
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

		assertThatThrownBy(() -> client.recognize(new byte[] {1}))
			.isInstanceOf(RuntimeException.class).isNotInstanceOf(BusinessException.class);
	}

	@Test
	void 주소_끝의_슬래시는_지운다() {
		ReceiptOcrClient client = client(new OcrProperties("http://ocr.test/ ", Duration.ofSeconds(2),
			Duration.ofSeconds(20), Duration.ofSeconds(30)));
		server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		client.recognize(new byte[] {1});

		server.verify();
	}

	@Test
	void 주소가_없으면_부르지_않고_일시_오류() {
		ReceiptOcrClient client = client(new OcrProperties(" ", Duration.ofSeconds(2), Duration.ofSeconds(20), Duration.ofSeconds(30)));

		assertThatThrownBy(() -> client.recognize(new byte[] {1})).isInstanceOf(IllegalStateException.class);
		server.verify();
	}

}
