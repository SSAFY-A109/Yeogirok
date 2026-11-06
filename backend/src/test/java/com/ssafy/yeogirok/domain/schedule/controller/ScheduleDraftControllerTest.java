package com.ssafy.yeogirok.domain.schedule.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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

import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.draft.ScheduleDraftService;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand.FixedItem;
import com.ssafy.yeogirok.domain.schedule.dto.DraftJobResponse;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 일정 초안 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 ScheduleDraftServiceTest 가 본다. */
@WebMvcTest(ScheduleDraftController.class)
@Import(WebMvcSecurity.class)
class ScheduleDraftControllerTest {

	static final Long USER_ID = 1L;
	static final LocalDate DAY = LocalDate.of(2026, 11, 8);
	static final String JOB_ID = "550e8400-e29b-41d4-a716-446655440000";
	static final String DRAFT_KEY = "550e8400-e29b-41d4-a716-446655440001";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ScheduleDraftService scheduleDraftService;
	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	private ResultActions postJson(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/rooms/7/schedule-drafts").with(asUser(USER_ID))
			.header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/* ── API-037 ────────────────────────────────────────────── */

	@Test
	void 초안_생성_요청을_읽어_넘기고_202로_작업과_초안_키를_돌려준다() throws Exception {
		given(scheduleDraftService.create(eq(USER_ID), eq(7L), any()))
			.willReturn(DraftJobResponse.queued(JOB_ID, DRAFT_KEY));

		postJson("""
			{"baseVersion": 3, "roomPlaceIds": [401, 402, 403],
			 "days": [{"date": "2026-11-08", "startTime": "09:00:00", "endTime": "18:00:00"}],
			 "startPoint": {"name": "강릉역", "latitude": 37.764, "longitude": 128.899},
			 "endPoint": null,
			 "stayMinutes": {"401": 45},
			 "mealConstraints": {"anything": true},
			 "fixedItems": [{"roomPlaceId": 402, "visitDate": "2026-11-08", "order": 1, "stayMinutes": 30,
			   "fixed": true}]}
			""")
			.andExpect(status().isAccepted())
			.andExpect(content().json("""
				{"success": true, "data": {"jobId": "550e8400-e29b-41d4-a716-446655440000",
				  "draftKey": "550e8400-e29b-41d4-a716-446655440001", "status": "QUEUED"}}
				""", JsonCompareMode.STRICT));

		DraftCreateCommand expected = new DraftCreateCommand(3, List.of(401L, 402L, 403L),
			List.of(new DayOption(DAY, LocalTime.of(9, 0), LocalTime.of(18, 0))),
			new NamedPoint("강릉역", new BigDecimal("37.764"), new BigDecimal("128.899")), null, Map.of(401L, 45),
			List.of(new FixedItem(402L, DAY, 1, 30, true)));
		verify(scheduleDraftService).create(USER_ID, 7L, expected);
		ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
		verify(idempotencyService).execute(key.capture(), eq(expected), any(), any(), any());
		assertThat(key.getValue()).isEqualTo(new IdempotencyKey(USER_ID, "schedule-draft-create", "7", "key-1"));
	}

	@Test
	void 선택_필드는_생략할_수_있다() throws Exception {
		postJson("""
			{"baseVersion": 3, "roomPlaceIds": [401, 402, 403],
			 "days": [{"date": "2026-11-08", "startTime": "09:00:00", "endTime": "18:00:00"}]}
			""").andExpect(status().isAccepted());

		verify(scheduleDraftService).create(USER_ID, 7L, new DraftCreateCommand(3, List.of(401L, 402L, 403L),
			List.of(new DayOption(DAY, LocalTime.of(9, 0), LocalTime.of(18, 0))), null, null, Map.of(), List.of()));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"roomPlaceIds\": [1], \"days\": []}                                                     | C001",
		"{\"baseVersion\": 1, \"days\": []}                                                        | C001",
		"{\"baseVersion\": 1, \"roomPlaceIds\": [1]}                                               | C001",
		"{\"baseVersion\": 1, \"roomPlaceIds\": \"1\", \"days\": []}                               | C002",
		"{\"baseVersion\": 1, \"roomPlaceIds\": [1], \"days\": [{\"date\": \"2026-11-08\", \"startTime\": \"09:00\", \"endTime\": \"18:00:00\"}]} | C002",
		"{\"baseVersion\": 1, \"roomPlaceIds\": [1], \"days\": [{\"date\": \"2026-11-08\", \"endTime\": \"18:00:00\"}]} | C001",
		"{\"baseVersion\": 1, \"roomPlaceIds\": [1], \"days\": [], \"startPoint\": {\"latitude\": 91, \"longitude\": 128}} | C011",
		"{\"baseVersion\": 1, \"roomPlaceIds\": [1], \"days\": [], \"startPoint\": {\"latitude\": \"37\", \"longitude\": 128}} | C002",
		"{\"baseVersion\": 1, \"roomPlaceIds\": [1], \"days\": [], \"stayMinutes\": {\"a\": 30}}   | C002",
		"{\"baseVersion\": 1, \"roomPlaceIds\": [1], \"days\": [], \"stayMinutes\": {\"1\": 1.5}}  | C002",
		"{\"baseVersion\": 1, \"roomPlaceIds\": [1], \"days\": [], \"fixedItems\": [{\"roomPlaceId\": 1, \"visitDate\": \"2026-11-08\", \"order\": 1}]} | C001",
	})
	void 초안_생성_요청_형식_오류는_서비스를_부르지_않는다(String json, String code) throws Exception {
		postJson(json)
			.andExpect(status().is4xxClientError())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(scheduleDraftService);
	}

	@Test
	void 멱등키가_없으면_C001() throws Exception {
		mockMvc.perform(post("/api/v1/rooms/7/schedule-drafts").with(asUser(USER_ID))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
	}
}
