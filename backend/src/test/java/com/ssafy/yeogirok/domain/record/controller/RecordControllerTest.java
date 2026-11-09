package com.ssafy.yeogirok.domain.record.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.dto.ActualVisitView;
import com.ssafy.yeogirok.domain.record.dto.RecordCreateResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordDetailResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPhotoView;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 여행 기록 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 서비스 테스트가 본다. */
@WebMvcTest(RecordController.class)
@Import(WebMvcSecurity.class)
class RecordControllerTest {

	static final Long USER_ID = 1L;

	@Autowired
	MockMvc mockMvc;
	@MockitoBean
	RecordCreateService recordCreateService;
	@MockitoBean
	RecordQueryService recordQueryService;
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
	class API_057 {

		@Test
		void 생성_응답은_201() throws Exception {
			given(recordCreateService.create(USER_ID, 7L, "가을 여행"))
				.willReturn(new RecordCreateResponse(201L, 7L, RecordStatus.DRAFT, 1));

			mockMvc.perform(json(post("/api/v1/rooms/7/records"), "{\"title\": \"  가을 여행 \"}"))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
					{"success": true, "data": {"recordId": 201, "roomId": 7, "status": "DRAFT", "version": 1}}
					"""));
		}

		@Test
		void 본문이나_제목을_생략하면_제목_없이_넘긴다() throws Exception {
			given(recordCreateService.create(eq(USER_ID), eq(7L), isNull()))
				.willReturn(new RecordCreateResponse(201L, 7L, RecordStatus.DRAFT, 1));

			mockMvc.perform(post("/api/v1/rooms/7/records").with(asUser(USER_ID)).header("Idempotency-Key", "k"))
				.andExpect(status().isCreated());
			mockMvc.perform(json(post("/api/v1/rooms/7/records"), "{\"title\": null}"))
				.andExpect(status().isCreated());
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"title\": 3}|C002",
			"{\"title\": \"   \"}|C003"
		})
		void 제목_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post("/api/v1/rooms/7/records"), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(recordCreateService);
		}

		@Test
		void 제목_200자_초과는_C003() throws Exception {
			mockMvc.perform(json(post("/api/v1/rooms/7/records"), "{\"title\": \"" + "가".repeat(201) + "\"}"))
				.andExpect(jsonPath("$.code").value("C003"));
		}

		@Test
		void 멱등키가_없으면_C001() throws Exception {
			mockMvc.perform(post("/api/v1/rooms/7/records").with(asUser(USER_ID))
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C001"));
		}
	}

	@Nested
	class API_059 {

		@Test
		void 상세_응답_모양() throws Exception {
			RecordPhotoView photo = new RecordPhotoView(31L, null, null, 701L);
			ActualVisitView visit = new ActualVisitView(701L, new RecordPlaceView(PlaceProvider.MANUAL, null, "경포대",
				new BigDecimal("37.7519000"), new BigDecimal("128.8761000"), null, null, null, null), null,
				LocalDate.parse("2026-11-01"), Instant.parse("2026-11-01T00:30:00Z"), 1, List.of(photo),
				List.of(new ActualVisitView.Story(41L, 2L, "좋았다")));
			given(recordQueryService.getDetail(USER_ID, 9L)).willReturn(new RecordDetailResponse(9L, "가을 여행",
				RecordStatus.DRAFT, 7L, List.of(new RecordDetailResponse.Member(1L, "여행자", true)),
				LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"),
				new RecordProgress(RecordProgress.Stage.EDIT, null, null), null, List.of(), List.of(photo),
				List.of(visit), List.of(), null, 3));

			mockMvc.perform(get("/api/v1/records/9").with(asUser(USER_ID)))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success": true, "data": {
					  "id": 9, "title": "가을 여행", "status": "DRAFT", "roomId": 7,
					  "members": [{"userId": 1, "nickname": "여행자", "isCreator": true}],
					  "startDate": "2026-11-01", "endDate": "2026-11-02",
					  "progress": {"stage": "EDIT", "draftKey": null, "jobId": null},
					  "plannedSchedule": null, "uploads": [],
					  "photoPool": [{"imageId": 31, "attribution": null, "uploadedBy": null, "visitId": 701}],
					  "actualVisits": [{
					    "id": 701,
					    "place": {"provider": "MANUAL", "providerPlaceId": null, "name": "경포대",
					      "latitude": 37.7519000, "longitude": 128.8761000, "address": null, "categoryCode": null,
					      "imageUrl": null, "imageAttribution": null},
					    "plannedItemId": null, "visitDate": "2026-11-01", "visitedAt": "2026-11-01 09:30:00+09",
					    "order": 1,
					    "photos": [{"imageId": 31, "attribution": null, "uploadedBy": null, "visitId": 701}],
					    "stories": [{"id": 41, "authorId": 2, "body": "좋았다"}]
					  }],
					  "materials": [], "publishedCourseId": null, "version": 3
					}}
					""", JsonCompareMode.STRICT));
		}
	}
}
