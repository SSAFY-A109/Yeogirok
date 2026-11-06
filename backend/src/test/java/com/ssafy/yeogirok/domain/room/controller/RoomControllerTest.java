package com.ssafy.yeogirok.domain.room.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ssafy.yeogirok.domain.room.dto.ChatReadState;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomDetailResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomInvitationResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomJoinResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateResponse;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.service.RoomInvitationService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/**
 * 여행방 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 RoomServiceTest·RoomInvitationServiceTest,
 * 멱등키 동작 자체는 IdempotencyServiceTest 가 본다.
 */
@WebMvcTest(RoomController.class)
@Import(WebMvcSecurity.class)
class RoomControllerTest {

	static final Long USER_ID = 1L;
	static final LocalDate START = LocalDate.of(2026, 11, 8);

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RoomService roomService;

	@MockitoBean
	RoomInvitationService roomInvitationService;

	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	private ResultActions postJson(String url, String idempotencyKey, String json) throws Exception {
		return mockMvc.perform(post(url).with(asUser(USER_ID)).header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions patchJson(String json) throws Exception {
		return mockMvc.perform(patch("/api/v1/rooms/7").with(asUser(USER_ID))
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/* ── API-021 ────────────────────────────────────────────── */

	@Test
	void 생성하면_201과_명세의_응답을_준다() throws Exception {
		given(roomService.create(eq(USER_ID), any())).willReturn(new RoomCreateResponse(7L, 1, true));

		postJson("/api/v1/rooms", "key-1", """
			{"title": "가을 여행", "startDate": "2026-11-08", "endDate": "2026-11-09", "transportMode": "CAR"}
			""")
			.andExpect(status().isCreated())
			.andExpect(content().json("""
				{"success": true, "data": {"id": 7, "version": 1, "isCreator": true}}
				""", JsonCompareMode.STRICT));

		ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
		ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
		verify(idempotencyService).execute(key.capture(), request.capture(), any(), any(), any());
		assertThat(key.getValue()).isEqualTo(new IdempotencyKey(USER_ID, "room-create", "-", "key-1"));
		assertThat(request.getValue()).isEqualTo(
			new RoomCreateCommand("가을 여행", START, START.plusDays(1), TransportMode.CAR));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"title\": \"t\"}                                         | C001",
		"{\"title\": \"t\", \"transportMode\": null}                | C001",
		"{\"transportMode\": \"CAR\"}                               | C001",
		"{\"title\": null, \"transportMode\": \"CAR\"}              | C001",
		"{\"title\": 1, \"transportMode\": \"CAR\"}                 | C002",
		"{\"title\": \"t\", \"transportMode\": 1}                   | C002",
		"{\"title\": \"t\", \"transportMode\": \"BIKE\"}            | C003",
		"{\"title\": \"t\", \"transportMode\": \"CAR\", \"startDate\": \"11/08\", \"endDate\": null} | C002",
	})
	void 생성_요청_형식_오류(String json, String code) throws Exception {
		postJson("/api/v1/rooms", "key-1", json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(roomService);
	}

	@Test
	void 멱등키_헤더가_없으면_C001() throws Exception {
		mockMvc.perform(post("/api/v1/rooms").with(asUser(USER_ID)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"t\", \"transportMode\": \"CAR\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
	}

	/* ── API-020·022 ────────────────────────────────────────── */

	@Test
	void 목록_응답_모양() throws Exception {
		given(roomService.getMyRooms(USER_ID, null, null)).willReturn(new RoomListResponse(List.of(
			new RoomListResponse.Item(7L, "가을 여행", START, START.plusDays(1), TransportMode.CAR, 1,
				List.of(new RoomListResponse.Member(USER_ID, "여행자", null)), 0, List.of(), ChatReadState.of(null))),
			null));

		mockMvc.perform(get("/api/v1/rooms").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"items": [{
				  "id": 7, "title": "가을 여행", "startDate": "2026-11-08", "endDate": "2026-11-09",
				  "transportMode": "CAR", "memberCount": 1,
				  "members": [{"userId": 1, "nickname": "여행자", "profileImageUrl": null}],
				  "placeCount": 0, "recordIds": [],
				  "chatReadState": {"lastReadMessageId": null, "hasUnreadMessages": false}
				}], "nextCursor": null}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 상세_응답_모양() throws Exception {
		given(roomService.getRoom(USER_ID, 7L)).willReturn(new RoomDetailResponse(7L, "가을 여행", null, null,
			TransportMode.TRANSIT, List.of(new RoomDetailResponse.Member(USER_ID, "여행자", true)), List.of(), null,
			List.of(), 3, ChatReadState.of(5L)));

		mockMvc.perform(get("/api/v1/rooms/7").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {
				  "id": 7, "title": "가을 여행", "startDate": null, "endDate": null, "transportMode": "TRANSIT",
				  "members": [{"userId": 1, "nickname": "여행자", "isCreator": true}],
				  "places": [], "scheduleId": null, "recordIds": [], "version": 3,
				  "chatReadState": {"lastReadMessageId": 5, "hasUnreadMessages": false}
				}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 참여자가_아니면_403_RO002() throws Exception {
		given(roomService.getRoom(USER_ID, 7L)).willThrow(new BusinessException(ExceptionType.ROOM_NOT_MEMBER));

		mockMvc.perform(get("/api/v1/rooms/7").with(asUser(USER_ID)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RO002"));
	}

	@Test
	void 방_ID가_숫자가_아니면_C002() throws Exception {
		mockMvc.perform(get("/api/v1/rooms/abc").with(asUser(USER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C002"));
	}

	/* ── API-023 ────────────────────────────────────────────── */

	@Test
	void 수정은_생략한_필드를_유지값으로_넘긴다() throws Exception {
		given(roomService.update(eq(USER_ID), eq(7L), any())).willReturn(
			new RoomUpdateResponse(7L, 2, true, false, List.of(), null));

		patchJson("{\"version\": 1, \"transportMode\": \"TRANSIT\"}")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"id": 7, "version": 2, "applied": true,
				  "scheduleNeedsRecalculation": false, "affectedItems": [], "schedule": null}}
				""", JsonCompareMode.STRICT));

		verify(roomService).update(USER_ID, 7L,
			new RoomUpdateCommand(1, null, false, null, null, TransportMode.TRANSIT, false));
	}

	@Test
	void 날짜를_둘_다_null로_보내면_미정으로_바꾼다() throws Exception {
		patchJson("{\"version\": 1, \"startDate\": null, \"endDate\": null, \"previewOnly\": true}");

		verify(roomService).update(USER_ID, 7L, new RoomUpdateCommand(1, null, true, null, null, null, true));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"title\": \"t\"}                                | C001",
		"{\"version\": \"1\"}                               | C002",
		"{\"version\": 1, \"title\": null}                 | C003",
		"{\"version\": 1, \"title\": 3}                    | C002",
		"{\"version\": 1, \"transportMode\": null}         | C003",
		"{\"version\": 1, \"transportMode\": \"WALK\"}     | C003",
		"{\"version\": 1, \"startDate\": \"2026-11-08\"}    | C012",
		"{\"version\": 1, \"previewOnly\": \"yes\"}         | C002",
	})
	void 수정_요청_형식_오류(String json, String code) throws Exception {
		patchJson(json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(roomService);
	}

	/* ── API-024·025 ────────────────────────────────────────── */

	@Test
	void 초대_링크를_처음_만들면_201_있으면_200() throws Exception {
		RoomInvitationResponse created = new RoomInvitationResponse(7L, "tok", null, "https://x/invite/tok", true);
		given(roomInvitationService.getOrCreateInvitation(USER_ID, 7L)).willReturn(created);

		mockMvc.perform(post("/api/v1/rooms/7/invitations").with(asUser(USER_ID)).header("Idempotency-Key", "k"))
			.andExpect(status().isCreated())
			.andExpect(content().json("""
				{"success": true, "data": {"invitationId": 7, "inviteToken": "tok", "expiresAt": null,
				  "inviteUrl": "https://x/invite/tok"}}
				""", JsonCompareMode.STRICT));

		given(roomInvitationService.getOrCreateInvitation(USER_ID, 7L)).willReturn(
			new RoomInvitationResponse(7L, "tok", null, "https://x/invite/tok", false));
		mockMvc.perform(post("/api/v1/rooms/7/invitations").with(asUser(USER_ID)).header("Idempotency-Key", "k2"))
			.andExpect(status().isOk());

		ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
		verify(idempotencyService, times(2)).execute(key.capture(), any(), any(), any(), any());
		assertThat(key.getAllValues().getFirst())
			.isEqualTo(new IdempotencyKey(USER_ID, "room-invitation-create", "7", "k"));
	}

	@Test
	void 초대_링크로_참여하면_200() throws Exception {
		given(roomInvitationService.accept(USER_ID, "tok")).willReturn(RoomJoinResponse.joined(7L, false));

		postJson("/api/v1/room-invitations/accept", "k", "{\"inviteToken\": \"tok\"}")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"roomId": 7, "membershipStatus": "JOINED", "isCreator": false}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 참여_토큰이_없으면_C001_만원이면_409_RO007() throws Exception {
		postJson("/api/v1/room-invitations/accept", "k", "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));

		given(roomInvitationService.accept(USER_ID, "tok")).willThrow(new BusinessException(ExceptionType.ROOM_FULL));
		postJson("/api/v1/room-invitations/accept", "k", "{\"inviteToken\": \"tok\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RO007"));
	}

	/* ── API-087 ────────────────────────────────────────────── */

	@Test
	void 나가기는_빈_성공을_준다() throws Exception {
		mockMvc.perform(delete("/api/v1/rooms/7/members/me").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"success\": true, \"data\": null}", JsonCompareMode.STRICT));

		verify(roomService).leave(USER_ID, 7L);
	}
}
