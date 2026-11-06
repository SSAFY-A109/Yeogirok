package com.ssafy.yeogirok.domain.schedule.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleResponse;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand.ItemEdit;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateResponse;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 일정 HTTP 계약(경로·요청 형식 오류 코드·응답 모양). 값 규칙은 ScheduleServiceTest 가 본다. */
@WebMvcTest(ScheduleController.class)
@Import(WebMvcSecurity.class)
class ScheduleControllerTest {

	static final Long USER_ID = 1L;
	static final LocalDate DAY = LocalDate.of(2026, 10, 23);

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ScheduleService scheduleService;

	private ResultActions patchJson(String json) throws Exception {
		return mockMvc.perform(patch("/api/v1/rooms/7/schedule").with(asUser(USER_ID))
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static ScheduleView schedule() {
		return new ScheduleView(List.of(new ScheduleView.Day(DAY, LocalTime.of(9, 0), LocalTime.of(18, 0),
			List.of(new ScheduleView.Item(1701L, 401L, 1, Instant.parse("2026-10-23T00:30:00Z"),
				Instant.parse("2026-10-23T01:30:00Z"), 60, false)),
			List.of(new ScheduleView.Leg(null, 1701L, TransportMode.CAR, 30, "KAKAO_CAR")), List.of())),
			List.of(new ScheduleView.Unassigned(402L)), List.of(),
			new ScheduleView.Point("강릉역", new BigDecimal("37.5"), new BigDecimal("127.0")), null);
	}

	/* ── API-036 ────────────────────────────────────────────── */

	@Test
	void 조회_응답_모양() throws Exception {
		given(scheduleService.getSchedule(USER_ID, 7L)).willReturn(new ScheduleResponse(schedule(), 3));

		mockMvc.perform(get("/api/v1/rooms/7/schedule").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"schedule": {"days": [{
				  "date": "2026-10-23", "startTime": "09:00:00", "endTime": "18:00:00",
				  "items": [{"id": 1701, "roomPlaceId": 401, "order": 1,
				    "arrivalAt": "2026-10-23 09:30:00+09", "departureAt": "2026-10-23 10:30:00+09",
				    "stayMinutes": 60, "fixed": false}],
				  "legs": [{"fromItemId": null, "toItemId": 1701, "transportMode": "CAR", "travelMinutes": 30,
				    "source": "KAKAO_CAR"}],
				  "warnings": []}],
				  "unassignedPlaces": [{"roomPlaceId": 402}],
				  "warnings": [],
				  "startPoint": {"name": "강릉역", "latitude": 37.5, "longitude": 127.0}, "endPoint": null},
				  "version": 3}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 일정이_없으면_schedule은_null() throws Exception {
		given(scheduleService.getSchedule(USER_ID, 7L)).willReturn(new ScheduleResponse(null, 1));

		mockMvc.perform(get("/api/v1/rooms/7/schedule").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"success\": true, \"data\": {\"schedule\": null, \"version\": 1}}",
				JsonCompareMode.STRICT));
	}

	@Test
	void 참여자가_아니면_403_RO002() throws Exception {
		given(scheduleService.getSchedule(USER_ID, 7L)).willThrow(new BusinessException(ExceptionType.ROOM_NOT_MEMBER));

		mockMvc.perform(get("/api/v1/rooms/7/schedule").with(asUser(USER_ID)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RO002"));
	}

	/* ── API-040 ────────────────────────────────────────────── */

	@Test
	void 편집_요청을_읽어_넘기고_응답에_재계산_표시를_담는다() throws Exception {
		given(scheduleService.update(eq(USER_ID), eq(7L), any()))
			.willReturn(new ScheduleUpdateResponse(schedule(), 2, false));

		patchJson("""
			{"version": 1, "items": [
			  {"id": 1, "roomPlaceId": 401, "visitDate": "2026-10-23", "order": 1,
			   "arrivalAt": "2026-10-23 09:00:00+09", "stayMinutes": 60, "fixed": true},
			  {"roomPlaceId": 402, "visitDate": "2026-10-23", "order": 2, "stayMinutes": 30}],
			 "removeItemIds": [3]}
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").value(2))
			.andExpect(jsonPath("$.data.needsRecalculation").value(false))
			.andExpect(jsonPath("$.data.schedule.startPoint.name").value("강릉역"));

		verify(scheduleService).update(USER_ID, 7L, new ScheduleUpdateCommand(1, List.of(
			new ItemEdit(1L, 401L, DAY, 1, Instant.parse("2026-10-23T00:00:00Z"), 60, true),
			new ItemEdit(null, 402L, DAY, 2, null, 30, null)), List.of(3L)));
	}

	@Test
	void removeItemIds는_생략할_수_있다() throws Exception {
		patchJson("{\"version\": 1, \"items\": []}");

		verify(scheduleService).update(USER_ID, 7L, new ScheduleUpdateCommand(1, List.of(), List.of()));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"items\": []}                                                                    | C001",
		"{\"version\": 1}                                                                   | C001",
		"{\"version\": \"1\", \"items\": []}                                               | C002",
		"{\"version\": 1, \"items\": {}}                                                   | C002",
		"{\"version\": 1, \"items\": [3]}                                                  | C002",
		"{\"version\": 1, \"items\": [{\"visitDate\": \"2026-10-23\", \"order\": 1, \"stayMinutes\": 60}]} | C001",
		"{\"version\": 1, \"items\": [{\"roomPlaceId\": null, \"visitDate\": \"2026-10-23\", \"order\": 1, \"stayMinutes\": 60}]} | C002",
		"{\"version\": 1, \"items\": [{\"roomPlaceId\": 1, \"visitDate\": \"10/23\", \"order\": 1, \"stayMinutes\": 60}]} | C002",
		"{\"version\": 1, \"items\": [{\"roomPlaceId\": 1, \"visitDate\": \"2026-10-23\", \"stayMinutes\": 60}]} | C001",
		"{\"version\": 1, \"items\": [{\"roomPlaceId\": 1, \"visitDate\": \"2026-10-23\", \"order\": 1, \"stayMinutes\": 60, \"arrivalAt\": \"2026-10-23T09:00\"}]} | C002",
		"{\"version\": 1, \"items\": [{\"roomPlaceId\": 1, \"visitDate\": \"2026-10-23\", \"order\": 1, \"stayMinutes\": 60, \"fixed\": \"Y\"}]} | C002",
		"{\"version\": 1, \"items\": [], \"removeItemIds\": [\"a\"]}                        | C002",
	})
	void 요청_형식_오류는_서비스를_부르지_않는다(String json, String code) throws Exception {
		patchJson(json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(scheduleService);
	}
}
