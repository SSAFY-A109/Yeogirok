package com.ssafy.yeogirok.domain.record.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ssafy.yeogirok.domain.record.dto.RecordCreateResponse;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 여행 기록 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 서비스 테스트가 본다. */
@WebMvcTest(RecordController.class)
@Import(WebMvcSecurity.class)
class RecordControllerTest {

	static final Long USER_ID = 1L;

	@Autowired
	MockMvc mockMvc;
	@MockitoBean
	RecordCreateService recordCreateService;
	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.with(asUser(USER_ID)).header("Idempotency-Key", "key-1")
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Nested
	class API_057 {

		@Test
		void 생성_응답은_201() throws Exception {
			given(recordCreateService.create(USER_ID, 7L, "가을 여행"))
				.willReturn(new RecordCreateResponse(201L, 7L, RecordStatus.DRAFT, 1));

			mockMvc.perform(json(post("/api/v1/rooms/7/records"), "{\"title\": \"  가을 여행 \"}"))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
					{"success": true, "data": {"recordId": 201, "roomId": 7, "status": "DRAFT", "version": 1}}
					"""));
		}

		@Test
		void 본문이나_제목을_생략하면_제목_없이_넘긴다() throws Exception {
			given(recordCreateService.create(eq(USER_ID), eq(7L), isNull()))
				.willReturn(new RecordCreateResponse(201L, 7L, RecordStatus.DRAFT, 1));

			mockMvc.perform(post("/api/v1/rooms/7/records").with(asUser(USER_ID)).header("Idempotency-Key", "k"))
				.andExpect(status().isCreated());
			mockMvc.perform(json(post("/api/v1/rooms/7/records"), "{\"title\": null}"))
				.andExpect(status().isCreated());
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"title\": 3}|C002",
			"{\"title\": \"   \"}|C003"
		})
		void 제목_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post("/api/v1/rooms/7/records"), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(recordCreateService);
		}

		@Test
		void 제목_200자_초과는_C003() throws Exception {
			mockMvc.perform(json(post("/api/v1/rooms/7/records"), "{\"title\": \"" + "가".repeat(201) + "\"}"))
				.andExpect(jsonPath("$.code").value("C003"));
		}

		@Test
		void 멱등키가_없으면_C001() throws Exception {
			mockMvc.perform(post("/api/v1/rooms/7/records").with(asUser(USER_ID))
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
		}
	}
}
