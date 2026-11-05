package com.ssafy.yeogirok.domain.room.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.PlaceInfo;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRemoveResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRestoreResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceState;
import com.ssafy.yeogirok.domain.room.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 후보 장소 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 RoomPlaceServiceTest 가 본다. */
@WebMvcTest(RoomPlaceController.class)
@Import(WebMvcSecurity.class)
class RoomPlaceControllerTest {

	static final Long USER_ID = 1L;
	static final BigDecimal LAT = new BigDecimal("37.7519000");
	static final BigDecimal LNG = new BigDecimal("128.8761000");
	static final PlaceInfo MANUAL_PLACE = new PlaceInfo(PlaceProvider.MANUAL, null, "경포대", LAT, LNG, null, null,
		null, null, "강릉동", null, null);

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RoomPlaceService roomPlaceService;

	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	private ResultActions postCustomPlace(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/rooms/7/custom-places").with(asUser(USER_ID))
			.header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions patchPlace(String json) throws Exception {
		return mockMvc.perform(patch("/api/v1/rooms/7/places/401").with(asUser(USER_ID))
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/* ── API-028 ────────────────────────────────────────────── */

	@Test
	void 목록_응답_모양() throws Exception {
		given(roomPlaceService.getPlaces(USER_ID, 7L, RoomPlaceState.REMOVED)).willReturn(new RoomPlaceListResponse(
			List.of(new RoomPlaceListResponse.Item(401L, MANUAL_PLACE, PlaceSourceType.MANUAL, null, null, 2L,
				Instant.parse("2026-11-05T01:30:00Z"))), 4));

		mockMvc.perform(get("/api/v1/rooms/7/places").param("state", "REMOVED").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"items": [{
				  "id": 401,
				  "place": {"provider": "MANUAL", "providerPlaceId": null, "name": "경포대",
				    "latitude": 37.7519000, "longitude": 128.8761000, "address": null,
				    "categoryCode": null, "categoryName": null, "regionCode": null, "regionName": "강릉동",
				    "imageUrl": null, "imageAttribution": null},
				  "sourceType": "MANUAL", "sourceRecordId": null, "sourceRecord": null, "addedBy": 2,
				  "removedAt": "2026-11-05 10:30:00+09"
				}], "version": 4}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void state를_생략하면_ACTIVE() throws Exception {
		given(roomPlaceService.getPlaces(USER_ID, 7L, RoomPlaceState.ACTIVE))
			.willReturn(new RoomPlaceListResponse(List.of(), 1));

		mockMvc.perform(get("/api/v1/rooms/7/places").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"active", "DELETED", ""})
	void 없는_state는_C003(String state) throws Exception {
		mockMvc.perform(get("/api/v1/rooms/7/places").param("state", state).with(asUser(USER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C003"));
		verifyNoInteractions(roomPlaceService);
	}

	/* ── API-031 ────────────────────────────────────────────── */

	@Test
	void 직접_추가하면_201과_명세의_응답을_주고_멱등키는_방_단위다() throws Exception {
		given(roomPlaceService.addCustomPlace(eq(USER_ID), eq(7L), any())).willReturn(new CustomPlaceCreateResponse(
			new RoomPlaceResponse(401L, MANUAL_PLACE, PlaceSourceType.MANUAL, null, USER_ID), 2));

		postCustomPlace("""
			{"name": "  경포대  ", "latitude": 37.7519, "longitude": 128.8761, "address": " 강원 강릉시 "}
			""")
			.andExpect(status().isCreated())
			.andExpect(content().json("""
				{"success": true, "data": {"roomPlace": {
				  "id": 401,
				  "place": {"provider": "MANUAL", "providerPlaceId": null, "name": "경포대",
				    "latitude": 37.7519000, "longitude": 128.8761000, "address": null,
				    "categoryCode": null, "categoryName": null, "regionCode": null, "regionName": "강릉동",
				    "imageUrl": null, "imageAttribution": null},
				  "sourceType": "MANUAL", "sourceRecordId": null, "addedBy": 1
				}, "version": 2}}
				""", JsonCompareMode.STRICT));

		ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
		ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
		verify(idempotencyService).execute(key.capture(), request.capture(), any(), any(), any());
		assertThat(key.getValue()).isEqualTo(new IdempotencyKey(USER_ID, "room-custom-place-create", "7", "key-1"));
		assertThat(request.getValue()).isEqualTo(new CustomPlaceCreateCommand("경포대", LAT, LNG, "강원 강릉시"));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"latitude\": 37.5, \"longitude\": 127}                     | C001",
		"{\"name\": null, \"latitude\": 37.5, \"longitude\": 127}     | C001",
		"{\"name\": \"a\", \"longitude\": 127}                        | C001",
		"{\"name\": \"a\", \"latitude\": 37.5, \"longitude\": null}   | C001",
		"{\"name\": 1, \"latitude\": 37.5, \"longitude\": 127}        | C002",
		"{\"name\": \"a\", \"latitude\": \"37.5\", \"longitude\": 127} | C002",
		"{\"name\": \"a\", \"latitude\": 37.5, \"longitude\": 127, \"address\": 1} | C002",
		"{\"name\": \"a\", \"latitude\": 90.1, \"longitude\": 127}    | C011",
		"{\"name\": \"a\", \"latitude\": 37.5, \"longitude\": -180.5} | C011",
		"{\"name\": \"a\", \"latitude\": 1e400, \"longitude\": 127}   | C011",
		"{\"name\": \"   \", \"latitude\": 37.5, \"longitude\": 127}  | P004",
		"{\"name\": \"\", \"latitude\": 37.5, \"longitude\": 127}     | P004",
	})
	void 직접_추가_요청_형식_오류(String json, String code) throws Exception {
		postCustomPlace(json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(roomPlaceService);
	}

	@Test
	void 장소명은_앞뒤_공백을_빼고_255자까지() throws Exception {
		given(roomPlaceService.addCustomPlace(eq(USER_ID), eq(7L), any())).willReturn(new CustomPlaceCreateResponse(
			new RoomPlaceResponse(401L, MANUAL_PLACE, PlaceSourceType.MANUAL, null, USER_ID), 2));
		String name255 = "가".repeat(255);

		postCustomPlace("{\"name\": \" " + name255 + " \", \"latitude\": 37.5, \"longitude\": 127}")
			.andExpect(status().isCreated());
		postCustomPlace("{\"name\": \"" + name255 + "가\", \"latitude\": 37.5, \"longitude\": 127}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("P004"));
	}

	@Test
	void 직접_추가에_멱등키_헤더가_없으면_C001() throws Exception {
		mockMvc.perform(post("/api/v1/rooms/7/custom-places").with(asUser(USER_ID))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"a\", \"latitude\": 37.5, \"longitude\": 127}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
	}

	/* ── API-032 ────────────────────────────────────────────── */

	@Test
	void 빼기_응답_모양() throws Exception {
		given(roomPlaceService.remove(USER_ID, 7L, 401L, 3)).willReturn(RoomPlaceRemoveResponse.of(4, true));

		mockMvc.perform(delete("/api/v1/rooms/7/places/401").param("version", "3").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"removed": true, "version": 4, "scheduleNeedsRecalculation": true,
				  "undoExpiresAt": null}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 빼기에_version이_없으면_C001_정수가_아니면_C002() throws Exception {
		mockMvc.perform(delete("/api/v1/rooms/7/places/401").with(asUser(USER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C001"));
		mockMvc.perform(delete("/api/v1/rooms/7/places/401").param("version", "abc").with(asUser(USER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C002"));
		verifyNoInteractions(roomPlaceService);
	}

	@Test
	void 없는_후보면_404_RO009() throws Exception {
		given(roomPlaceService.remove(USER_ID, 7L, 401L, 3))
			.willThrow(new BusinessException(ExceptionType.ROOM_PLACE_NOT_FOUND));

		mockMvc.perform(delete("/api/v1/rooms/7/places/401").param("version", "3").with(asUser(USER_ID)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RO009"));
	}

	/* ── API-094 ────────────────────────────────────────────── */

	@Test
	void 복원_응답_모양() throws Exception {
		given(roomPlaceService.restore(USER_ID, 7L, 401L, 3))
			.willReturn(RoomPlaceRestoreResponse.of(401L, 4, false));

		patchPlace("{\"version\": 3, \"action\": \"RESTORE\"}")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"restored": true, "roomPlaceId": 401, "version": 4,
				  "scheduleNeedsRecalculation": false}}
				""", JsonCompareMode.STRICT));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"action\": \"RESTORE\"}                  | C001",
		"{\"version\": 3}                           | C001",
		"{\"version\": null, \"action\": \"RESTORE\"} | C002",
		"{\"version\": \"3\", \"action\": \"RESTORE\"} | C002",
		"{\"version\": 3, \"action\": null}         | C002",
		"{\"version\": 3, \"action\": 1}            | C002",
		"{\"version\": 3, \"action\": \"REMOVE\"}   | C003",
		"{\"version\": 3, \"action\": \"restore\"}  | C003",
	})
	void 복원_요청_형식_오류(String json, String code) throws Exception {
		patchPlace(json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(roomPlaceService);
	}
}
