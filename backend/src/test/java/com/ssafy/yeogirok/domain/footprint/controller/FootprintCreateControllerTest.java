package com.ssafy.yeogirok.domain.footprint.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateCommand;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateResponse;
import com.ssafy.yeogirok.domain.footprint.dto.StatisticsStatus;
import com.ssafy.yeogirok.domain.footprint.service.FootprintCreateService;
import com.ssafy.yeogirok.domain.footprint.service.FootprintService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.security.PermitAllSecurityConfig;

/**
 * API-041 HTTP 계약(요청 형식 오류 코드, 응답 모양, 멱등키 연결). 값 규칙은 FootprintCreateServiceTest,
 * 멱등키 동작 자체(C005·C018·재조회)는 IdempotencyServiceTest 가 본다.
 */
@WebMvcTest(FootprintController.class)
@Import(PermitAllSecurityConfig.class)
class FootprintCreateControllerTest {

	static final Long USER_ID = 1L;
	static final String VALID = """
		{
		  "location": {"latitude": 37.50123456, "longitude": 127.0398765, "capturedAt": "2026-11-04 12:00:00+09"},
		  "body": "좋았다",
		  "categoryCodes": ["CAFE", "FOOD"]
		}
		""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	FootprintService footprintService;

	@MockitoBean
	FootprintCreateService footprintCreateService;

	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(2)).get());
	}

	@Test
	void 저장하면_201과_명세의_응답을_돌려준다() throws Exception {
		given(footprintCreateService.create(eq(USER_ID), any())).willReturn(new FootprintCreateResponse(
			10L, new BigDecimal("37.5012346"), new BigDecimal("127.0398765"), "좋았다", List.of("CAFE", "FOOD"),
			Instant.parse("2026-11-04T03:00:00Z"), Instant.parse("2026-11-04T03:00:05Z"), StatisticsStatus.APPLIED));

		postAs(USER_ID, "key-1", VALID)
			.andExpect(status().isCreated())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": {
				    "id": 10, "latitude": 37.5012346, "longitude": 127.0398765, "body": "좋았다",
				    "categoryCodes": ["CAFE", "FOOD"],
				    "visitedAt": "2026-11-04 12:00:00+09", "createdAt": "2026-11-04 12:00:05+09",
				    "statisticsStatus": "APPLIED"
				  }
				}
				"""));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 멱등키는_사용자_footprint_create_대상없음_헤더값으로_만들고_읽은_요청을_넘긴다() throws Exception {
		given(footprintCreateService.create(eq(USER_ID), any())).willReturn(response(10L));

		postAs(USER_ID, "key-1", VALID).andExpect(status().isCreated());

		ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
		ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
		ArgumentCaptor<Function<FootprintCreateResponse, ?>> resultIdOf = ArgumentCaptor.forClass(Function.class);
		ArgumentCaptor<Function<String, FootprintCreateResponse>> findByResultId =
			ArgumentCaptor.forClass(Function.class);
		verify(idempotencyService).execute(key.capture(), request.capture(), any(), resultIdOf.capture(),
			findByResultId.capture());

		assertThat(key.getValue()).isEqualTo(new IdempotencyKey(USER_ID, "footprint-create", "-", "key-1"));
		assertThat(request.getValue()).isEqualTo(new FootprintCreateCommand(
			new BigDecimal("37.5012346"),   // 소수 7자리로 반올림
			new BigDecimal("127.0398765"),
			Instant.parse("2026-11-04T03:00:00Z"),
			"좋았다",
			List.of("CAFE", "FOOD")));
		assertThat(resultIdOf.getValue().apply(response(10L))).isEqualTo(10L);

		given(footprintCreateService.getCreated(USER_ID, 10L)).willReturn(response(10L));
		assertThat(findByResultId.getValue().apply("10").id()).isEqualTo(10L);
	}

	@Test
	void body는_생략해도_된다() throws Exception {
		given(footprintCreateService.create(eq(USER_ID), any())).willReturn(response(10L));

		postAs(USER_ID, "key-1", """
			{"location": {"latitude": 37.5, "longitude": 127.0, "capturedAt": "2026-11-04 12:00:00+09"},
			 "categoryCodes": ["CAFE"]}
			""").andExpect(status().isCreated());

		ArgumentCaptor<FootprintCreateCommand> command = ArgumentCaptor.forClass(FootprintCreateCommand.class);
		verify(footprintCreateService).create(eq(USER_ID), command.capture());
		assertThat(command.getValue().body()).isNull();
	}

	@ParameterizedTest(name = "[{0}]")
	@NullSource
	@ValueSource(strings = " ")
	void Idempotency_Key가_없거나_비어_있으면_400_C001(String idempotencyKey) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/footprints").with(asUser(USER_ID))
			.contentType(MediaType.APPLICATION_JSON).content(VALID);
		if (idempotencyKey != null) {
			request.header("Idempotency-Key", idempotencyKey);
		}
		mockMvc.perform(request)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
		verifyNoInteractions(idempotencyService, footprintCreateService);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		location 생략          | {"categoryCodes": ["CAFE"]}
		location null          | {"location": null, "categoryCodes": ["CAFE"]}
		latitude 생략          | {"location": {"longitude": 127.0, "capturedAt": "2026-11-04 12:00:00+09"}, "categoryCodes": ["CAFE"]}
		longitude null         | {"location": {"latitude": 37.5, "longitude": null, "capturedAt": "2026-11-04 12:00:00+09"}, "categoryCodes": ["CAFE"]}
		capturedAt 생략        | {"location": {"latitude": 37.5, "longitude": 127.0}, "categoryCodes": ["CAFE"]}
		""")
	void 위치_증거가_없으면_422_C008(String name, String json) throws Exception {
		postAs(USER_ID, "key-1", json)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("C008"));
		verifyNoInteractions(idempotencyService);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		location 이 문자열      | {"location": "37.5,127.0", "categoryCodes": ["CAFE"]} | C002
		latitude 가 문자열      | {"location": {"latitude": "37.5", "longitude": 127.0, "capturedAt": "2026-11-04 12:00:00+09"}, "categoryCodes": ["CAFE"]} | C002
		capturedAt ISO 형식     | {"location": {"latitude": 37.5, "longitude": 127.0, "capturedAt": "2026-11-04T12:00:00+09:00"}, "categoryCodes": ["CAFE"]} | C002
		capturedAt 이 숫자      | {"location": {"latitude": 37.5, "longitude": 127.0, "capturedAt": 1762225200}, "categoryCodes": ["CAFE"]} | C002
		categoryCodes 가 문자열 | {"location": {"latitude": 37.5, "longitude": 127.0, "capturedAt": "2026-11-04 12:00:00+09"}, "categoryCodes": "CAFE"} | C002
		categoryCodes 에 숫자   | {"location": {"latitude": 37.5, "longitude": 127.0, "capturedAt": "2026-11-04 12:00:00+09"}, "categoryCodes": [1]} | C002
		body 가 숫자            | {"location": {"latitude": 37.5, "longitude": 127.0, "capturedAt": "2026-11-04 12:00:00+09"}, "body": 1, "categoryCodes": ["CAFE"]} | C002
		JSON 이 아님            | not-json | C002
		categoryCodes 생략     | {"location": {"latitude": 37.5, "longitude": 127.0, "capturedAt": "2026-11-04 12:00:00+09"}} | C001
		categoryCodes null     | {"location": {"latitude": 37.5, "longitude": 127.0, "capturedAt": "2026-11-04 12:00:00+09"}, "categoryCodes": null} | C001
		""")
	void 카테고리_생략은_C001_타입이나_형식_오류는_C002(String name, String json, String code) throws Exception {
		postAs(USER_ID, "key-1", json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(idempotencyService);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		NOT_ALLOWED_VALUE              | 400 | C003
		COORDINATE_OUT_OF_RANGE        | 400 | C011
		LOCATION_EVIDENCE_EXPIRED      | 422 | C009
		FOOTPRINT_TOO_CLOSE_TODAY      | 409 | F003
		IDEMPOTENCY_KEY_REUSED         | 409 | C005
		IDEMPOTENT_REQUEST_IN_PROGRESS | 409 | C018
		""")
	void 처리_중_실패는_명세의_코드로_응답한다(ExceptionType type, int status, String code) throws Exception {
		willThrow(new BusinessException(type)).given(idempotencyService).execute(any(), any(), any(), any(), any());

		postAs(USER_ID, "key-1", VALID)
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value(code));
	}

	@Test
	void 인증_정보가_없으면_401_A001() throws Exception {
		mockMvc.perform(post("/api/v1/footprints").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
		verifyNoInteractions(idempotencyService);
	}

	private static FootprintCreateResponse response(Long id) {
		return new FootprintCreateResponse(id, new BigDecimal("37.5000000"), new BigDecimal("127.0000000"), null,
			List.of("CAFE"), Instant.parse("2026-11-04T03:00:00Z"), Instant.parse("2026-11-04T03:00:05Z"),
			StatisticsStatus.APPLIED);
	}

	private ResultActions postAs(Long userId, String idempotencyKey, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/footprints").with(asUser(userId))
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser(Long userId) {
		return jwt().jwt(token -> token.subject(String.valueOf(userId)));
	}
}
