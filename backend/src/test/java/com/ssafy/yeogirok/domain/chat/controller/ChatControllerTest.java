package com.ssafy.yeogirok.domain.chat.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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

import com.ssafy.yeogirok.domain.chat.dto.ChatMessage;
import com.ssafy.yeogirok.domain.chat.dto.ChatMessagePage;
import com.ssafy.yeogirok.domain.chat.dto.ChatReadResponse;
import com.ssafy.yeogirok.domain.chat.dto.ChatTicketResponse;
import com.ssafy.yeogirok.domain.chat.service.ChatMessageService;
import com.ssafy.yeogirok.domain.chat.service.ChatReadService;
import com.ssafy.yeogirok.domain.chat.service.ChatTicketService;
import com.ssafy.yeogirok.domain.room.dto.ChatReadState;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 채팅 REST 계약(경로·요청 형식 오류 코드·응답 모양). 값 규칙은 ChatMessageServiceTest 가 본다. */
@WebMvcTest(ChatController.class)
@Import(WebMvcSecurity.class)
class ChatControllerTest {

	static final Long USER_ID = 1L;
	/** 2026-10-30 18:00 Asia/Seoul. */
	static final Instant AT = Instant.parse("2026-10-30T09:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ChatMessageService chatMessageService;
	@MockitoBean
	ChatReadService chatReadService;
	@MockitoBean
	ChatTicketService chatTicketService;
	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	@Test
	void 이력_응답_모양() throws Exception {
		given(chatMessageService.getMessages(USER_ID, 101L, null, 110L, null, 20)).willReturn(new ChatMessagePage(
			List.of(new ChatMessage(111L, new ChatMessage.Sender(1401L, "여행자"), "여기서 만나자!", AT, "cm-1"),
				new ChatMessage(112L, new ChatMessage.Sender(null, "탈퇴한 사용자"), "안녕", AT, null)),
			true, 112L, 120L, new ChatReadState(null, true)));

		mockMvc.perform(get("/api/v1/rooms/101/messages").with(asUser(USER_ID))
				.param("afterMessageId", "110").param("limit", "20"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {
				  "items": [
				    {"id": 111, "sender": {"id": 1401, "nickname": "여행자"}, "body": "여기서 만나자!",
				     "createdAt": "2026-10-30 18:00:00+09", "clientMessageId": "cm-1"},
				    {"id": 112, "sender": {"id": null, "nickname": "탈퇴한 사용자"}, "body": "안녕",
				     "createdAt": "2026-10-30 18:00:00+09", "clientMessageId": null}
				  ],
				  "hasMore": true, "nextAfterMessageId": 112, "replayThroughMessageId": 120,
				  "chatReadState": {"lastReadMessageId": null, "hasUnreadMessages": true}
				}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 커서가_정수가_아니면_C002() throws Exception {
		mockMvc.perform(get("/api/v1/rooms/101/messages").with(asUser(USER_ID)).param("beforeMessageId", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C002"));
		verifyNoInteractions(chatMessageService);
	}

	@Test
	void 읽은_위치_갱신() throws Exception {
		given(chatReadService.markRead(USER_ID, 101L, 111L)).willReturn(new ChatReadResponse(101L, 111L, false));

		mockMvc.perform(patch("/api/v1/rooms/101/messages/read").with(asUser(USER_ID))
				.contentType(MediaType.APPLICATION_JSON).content("{\"lastReadMessageId\": 111}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"roomId": 101, "lastReadMessageId": 111, "hasUnreadMessages": false}}
				""", JsonCompareMode.STRICT));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{}|C001",
		"{\"lastReadMessageId\": null}|C001",
		"{\"lastReadMessageId\": \"111\"}|C002",
		"{\"lastReadMessageId\": 1.5}|C002"
	})
	void 읽은_위치_요청_형식_오류(String body, String code) throws Exception {
		mockMvc.perform(patch("/api/v1/rooms/101/messages/read").with(asUser(USER_ID))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(chatReadService);
	}

	@Test
	void 티켓_발급은_201() throws Exception {
		given(chatTicketService.issue(USER_ID, 101L)).willReturn(
			new ChatTicketResponse("550e8400-e29b-41d4-a716-446655440030", AT, "/ws/v1", 101L));

		mockMvc.perform(post("/api/v1/realtime/tickets").with(asUser(USER_ID)).header("Idempotency-Key", "k-1")
				.contentType(MediaType.APPLICATION_JSON).content("{\"roomId\": 101}"))
			.andExpect(status().isCreated())
			.andExpect(content().json("""
				{"success": true, "data": {"ticket": "550e8400-e29b-41d4-a716-446655440030",
				  "expiresAt": "2026-10-30 18:00:00+09", "connectPath": "/ws/v1", "roomId": 101}}
				""", JsonCompareMode.STRICT));
		verify(chatTicketService).issue(USER_ID, 101L);
	}

	@Test
	void 티켓_발급은_멱등키와_roomId_가_필요하다() throws Exception {
		mockMvc.perform(post("/api/v1/realtime/tickets").with(asUser(USER_ID))
				.contentType(MediaType.APPLICATION_JSON).content("{\"roomId\": 101}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
		mockMvc.perform(post("/api/v1/realtime/tickets").with(asUser(USER_ID)).header("Idempotency-Key", "k-1")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
		verifyNoInteractions(chatTicketService);
	}

	@Test
	void 토큰이_없으면_A001() throws Exception {
		mockMvc.perform(get("/api/v1/rooms/101/messages"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}
}
