package com.ssafy.yeogirok.domain.record.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ssafy.yeogirok.domain.record.dto.MaterialLinkCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkResponse;
import com.ssafy.yeogirok.domain.record.material.MaterialLinkService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 영수증 자료 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 서비스 테스트가 본다. */
@WebMvcTest(RecordMaterialController.class)
@Import(WebMvcSecurity.class)
class RecordMaterialControllerTest {

	static final Long USER_ID = 1L;
	static final String UPLOAD_ID = "0a1b2c3d-0000-4000-8000-000000000001";

	@Autowired
	MockMvc mockMvc;
	@MockitoBean
	MaterialLinkService materialLinkService;
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
	class API_062 {

		static final String PATH = "/api/v1/records/10/materials";

		@Test
		void 연결_응답은_201() throws Exception {
			MaterialLinkCommand command = new MaterialLinkCommand(MaterialLinkCommand.Type.RECEIPT, UPLOAD_ID);
			given(materialLinkService.link(USER_ID, 10L, command))
				.willReturn(MaterialLinkResponse.queued("m-1", 10L, "job-1"));

			mockMvc.perform(json(post(PATH), "{\"type\":\"RECEIPT\",\"uploadId\":\"" + UPLOAD_ID + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
					{"success":true,"data":{"materialId":"m-1","recordId":10,"status":"UPLOADED","version":1,
					 "jobId":"job-1","ocrStatus":"QUEUED"}}
					""", JsonCompareMode.STRICT));
			verify(idempotencyService).execute(eq(new IdempotencyKey(USER_ID, RecordMaterialController.LINK_SCOPE,
				"10", "key-1")), eq(command), any(), any(), any());
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"uploadId\":\"" + UPLOAD_ID + "\"}|C001",
			"{\"type\":null,\"uploadId\":\"" + UPLOAD_ID + "\"}|C001",
			"{\"type\":1,\"uploadId\":\"" + UPLOAD_ID + "\"}|C002",
			"{\"type\":\"PHOTO\",\"uploadId\":\"" + UPLOAD_ID + "\"}|C003",
			"{\"type\":\"receipt\",\"uploadId\":\"" + UPLOAD_ID + "\"}|C003",
			"{\"type\":\"RECEIPT\"}|C001",
			"{\"type\":\"RECEIPT\",\"uploadId\":null}|C001",
			"{\"type\":\"RECEIPT\",\"uploadId\":\"not-uuid\"}|C002",
			"{\"type\":\"RECEIPT\",\"uploadId\":3}|C002"
		})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post(PATH), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(materialLinkService);
		}

		@Test
		void 멱등키가_없으면_C001() throws Exception {
			mockMvc.perform(post(PATH).with(asUser(USER_ID)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"type\":\"RECEIPT\",\"uploadId\":\"" + UPLOAD_ID + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
		}
	}
}
