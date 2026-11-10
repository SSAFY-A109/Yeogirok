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

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
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

import com.ssafy.yeogirok.domain.record.draft.RecordDraftService;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.record.dto.DraftJobResponse;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 통합 초안 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 서비스 테스트가 본다. */
@WebMvcTest(RecordDraftController.class)
@Import(WebMvcSecurity.class)
class RecordDraftControllerTest {

	static final Long USER_ID = 1L;
	static final String PATH = "/api/v1/records/10/drafts";
	static final String MATERIAL_ID = "11111111-2222-4333-8444-555555555555";
	static final String MATERIALS = "\"materials\":[{\"materialId\":\"" + MATERIAL_ID + "\",\"version\":3}]";

	@Autowired
	MockMvc mockMvc;
	@MockitoBean
	RecordDraftService recordDraftService;
	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	private static MockHttpServletRequestBuilder json(String body) {
		return post(PATH).with(asUser(USER_ID)).header("Idempotency-Key", "key-1")
			.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void 접수_응답은_202() throws Exception {
		DraftCreateCommand command = new DraftCreateCommand(1, List.of(801L, 802L),
			List.of(new DraftCreateCommand.MaterialRef(MATERIAL_ID, 3)));
		given(recordDraftService.create(USER_ID, 10L, command)).willReturn(DraftJobResponse.queued("d-1", "job-1"));

		mockMvc.perform(json("{\"baseVersion\":1,\"imageIds\":[801,802]," + MATERIALS + "}"))
			.andExpect(status().isAccepted())
			.andExpect(content().json("""
				{"success":true,"data":{"draftKey":"d-1","jobId":"job-1","status":"QUEUED"}}
				""", JsonCompareMode.STRICT));
		verify(idempotencyService).execute(eq(new IdempotencyKey(USER_ID, RecordDraftController.CREATE_SCOPE, "10",
			"key-1")), eq(command), any(), any(), any());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"imageIds\":[801]," + MATERIALS + "}|C001",
		"{\"baseVersion\":null,\"imageIds\":[801]," + MATERIALS + "}|C001",
		"{\"baseVersion\":\"1\",\"imageIds\":[801]," + MATERIALS + "}|C002",
		"{\"baseVersion\":1," + MATERIALS + "}|C001",
		"{\"baseVersion\":1,\"imageIds\":null," + MATERIALS + "}|C001",
		"{\"baseVersion\":1,\"imageIds\":801," + MATERIALS + "}|C002",
		"{\"baseVersion\":1,\"imageIds\":[\"801\"]," + MATERIALS + "}|C002",
		"{\"baseVersion\":1,\"imageIds\":[]," + MATERIALS + "}|C003",
		"{\"baseVersion\":1,\"imageIds\":[801,801]," + MATERIALS + "}|C003",
		"{\"baseVersion\":1,\"imageIds\":[801]}|C001",
		"{\"baseVersion\":1,\"imageIds\":[801],\"materials\":[]}|C003",
		"{\"baseVersion\":1,\"imageIds\":[801],\"materials\":[3]}|C002",
		"{\"baseVersion\":1,\"imageIds\":[801],\"materials\":[{\"version\":3}]}|C001",
		"{\"baseVersion\":1,\"imageIds\":[801],\"materials\":[{\"materialId\":\"x\",\"version\":3}]}|C002",
		"{\"baseVersion\":1,\"imageIds\":[801],\"materials\":[{\"materialId\":\"" + MATERIAL_ID + "\"}]}|C001",
		"{\"baseVersion\":1,\"imageIds\":[801],\"materials\":[{\"materialId\":\"" + MATERIAL_ID + "\",\"version\":3},"
			+ "{\"materialId\":\"" + MATERIAL_ID + "\",\"version\":3}]}|C003"
	})
	void 요청_형식_오류(String body, String code) throws Exception {
		mockMvc.perform(json(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(recordDraftService);
	}

	@Test
	void 사진이_100개를_넘으면_C003() throws Exception {
		String images = IntStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));

		mockMvc.perform(json("{\"baseVersion\":1,\"imageIds\":[" + images + "]," + MATERIALS + "}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C003"));
	}

	@Test
	void 멱등키가_없으면_C001() throws Exception {
		mockMvc.perform(post(PATH).with(asUser(USER_ID)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"baseVersion\":1,\"imageIds\":[801]," + MATERIALS + "}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
	}
}
