package com.ssafy.yeogirok.domain.file.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

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
import org.springframework.test.web.servlet.ResultActions;

import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateCommand;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateResponse;
import com.ssafy.yeogirok.domain.file.service.UploadService;
import com.ssafy.yeogirok.domain.file.session.UploadStatus;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** API-012 HTTP 계약: 응답 모양·요청 필드 오류 코드. 규칙은 UploadServiceTest 가 본다. */
@WebMvcTest(UploadController.class)
@Import(WebMvcSecurity.class)
class UploadControllerTest {

	static final Long USER_ID = 7L;
	static final String TIP = """
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1024 }
		""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	UploadService uploadService;

	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	@Test
	void 발급하면_201과_명세의_응답을_돌려준다() throws Exception {
		given(uploadService.create(eq(USER_ID),
			eq(new UploadCreateCommand(FilePurpose.TIP_PHOTO, "a.png", "image/png", 1024, null, null))))
			.willReturn(new UploadCreateResponse("550e8400-e29b-41d4-a716-446655440010", UploadStatus.UPLOADING,
				"image/png", 1024, "https://signed", "PUT", Map.of("Content-Type", "image/png"),
				Instant.parse("2026-11-06T00:10:00Z")));

		postAs("key-1", TIP)
			.andExpect(status().isCreated())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": {
				    "uploadId": "550e8400-e29b-41d4-a716-446655440010", "status": "UPLOADING",
				    "contentType": "image/png", "sizeBytes": 1024, "uploadUrl": "https://signed",
				    "uploadMethod": "PUT", "uploadHeaders": { "Content-Type": "image/png" },
				    "uploadExpiresAt": "2026-11-06 09:10:00+09"
				  }
				}
				""", JsonCompareMode.STRICT));
	}

	@ParameterizedTest(name = "{0} → {2}")
	@CsvSource(delimiter = '|', textBlock = """
		{ "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1 } | 400 | C001
		{ "purpose": 1, "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1 } | 400 | C002
		{ "purpose": "VIDEO", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1 } | 400 | C003
		{ "purpose": "tip_photo", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1 } | 400 | C003
		{ "purpose": "TIP_PHOTO", "contentType": "image/png", "sizeBytes": 1 } | 400 | C001
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "sizeBytes": 1 } | 400 | C001
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "contentType": null, "sizeBytes": 1 } | 400 | C002
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "contentType": "image/png" } | 400 | C001
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "contentType": "image/png", "sizeBytes": "1" } | 400 | C002
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1.5 } | 400 | C002
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 0 } | 400 | C003
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 99999999999999999999 } | 413 | M001
		{ "purpose": "TIP_PHOTO", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1, "recordId": "1" } | 400 | C002
		{ "purpose": "RECORD_PHOTO", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1 } | 400 | C001
		{ "purpose": "RECEIPT", "fileName": "a.png", "contentType": "image/png", "sizeBytes": 1, "recordId": null } | 400 | C001
		""")
	void 요청_필드_오류는_멱등키_처리_전에_알린다(String json, int status, String code) throws Exception {
		postAs("key-1", json)
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(idempotencyService);
	}

	@Test
	void Idempotency_Key_가_없으면_C001() throws Exception {
		mockMvc.perform(post("/api/v1/uploads").with(asUser(USER_ID))
				.contentType(MediaType.APPLICATION_JSON).content(TIP))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
	}

	private ResultActions postAs(String idempotencyKey, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/uploads").with(asUser(USER_ID))
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}
}
