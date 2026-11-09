package com.ssafy.yeogirok.domain.tip.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.yeogirok.domain.tip.dto.MyTipListResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.service.MyTipService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 작성자 본인 팁 원격 조회 HTTP 계약(응답 모양, 파라미터 연결). 규칙은 MyTipServiceTest 가 본다. */
@WebMvcTest(MyTipController.class)
@Import(WebMvcSecurity.class)
class MyTipControllerTest {

	static final Long USER_ID = 1L;

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MyTipService myTipService;

	@Nested
	class API_053_목록 {

		@Test
		void 앱이_검사하는_응답_모양을_돌려준다() throws Exception {
			given(myTipService.getMyTips(USER_ID, "FAILED", "c1", 10)).willReturn(new MyTipListResponse(List.of(
				new MyTipListResponse.Item(7L, ArTipCategory.TMI, TipStatus.FAILED, Instant.parse("2026-11-08T03:00:00Z"),
					Instant.parse("2026-11-08T03:00:05Z"))), null));

			mockMvc.perform(get("/api/v1/users/me/tips").with(asUser(USER_ID))
					.param("status", "FAILED").param("cursor", "c1").param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(7))
				.andExpect(jsonPath("$.data.items[0].category").value("TMI"))
				.andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-08 12:00:00+09"))
				.andExpect(jsonPath("$.data.items[0].updatedAt").value("2026-11-08 12:00:05+09"))
				.andExpect(jsonPath("$.data.nextCursor").hasJsonPath())
				.andExpect(jsonPath("$.data.nextCursor").isEmpty());
		}

		@Test
		void limit이_숫자가_아니면_C002() throws Exception {
			mockMvc.perform(get("/api/v1/users/me/tips").with(asUser(USER_ID)).param("limit", "ten"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C002"));
			verifyNoInteractions(myTipService);
		}
	}
}
