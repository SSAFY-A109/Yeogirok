package com.ssafy.yeogirok.domain.file.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.yeogirok.domain.file.dto.FileAccessResponse;
import com.ssafy.yeogirok.domain.file.service.FileAccessService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** API-013 HTTP 계약: 경로 2개의 응답 모양·경로 값 형식 오류. 규칙은 FileAccessServiceTest 가 본다. */
@WebMvcTest(FileAccessController.class)
@Import(WebMvcSecurity.class)
class FileAccessControllerTest {

	static final Long USER_ID = 7L;
	static final String MATERIAL_ID = "6f1c2a9e-0000-3000-8000-000000000001";
	static final FileAccessResponse RESPONSE = new FileAccessResponse("https://signed-get",
		Instant.parse("2026-11-06T00:15:00Z"), "image/jpeg");
	static final String JSON = """
		{ "success": true,
		  "data": { "url": "https://signed-get", "expiresAt": "2026-11-06 09:15:00+09", "contentType": "image/jpeg" } }
		""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	FileAccessService fileAccessService;

	@Test
	void 기록_사진_URL_은_명세의_응답으로_준다() throws Exception {
		given(fileAccessService.recordPhoto(USER_ID, 100L, 801L)).willReturn(RESPONSE);

		mockMvc.perform(get("/api/v1/records/100/images/801/access").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json(JSON, JsonCompareMode.STRICT));
	}

	@Test
	void 영수증_원본_URL_은_명세의_응답으로_준다() throws Exception {
		given(fileAccessService.receipt(USER_ID, 100L, MATERIAL_ID)).willReturn(RESPONSE);

		mockMvc.perform(get("/api/v1/records/100/materials/" + MATERIAL_ID + "/access").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json(JSON, JsonCompareMode.STRICT));
	}

	@ParameterizedTest
	@ValueSource(strings = {"/api/v1/records/abc/images/801/access", "/api/v1/records/100/images/x/access",
		"/api/v1/records/100/materials/not-a-uuid/access"})
	void 경로_값_형식이_틀리면_C002(String path) throws Exception {
		mockMvc.perform(get(path).with(asUser(USER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C002"));
		verifyNoInteractions(fileAccessService);
	}
}
