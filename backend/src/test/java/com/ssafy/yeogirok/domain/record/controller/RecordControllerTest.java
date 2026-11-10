package com.ssafy.yeogirok.domain.record.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
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
import com.ssafy.yeogirok.domain.record.dto.PhotoDeleteResponse;
import com.ssafy.yeogirok.domain.record.dto.PhotoLinkResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordCompleteResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordCreateResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordDetailResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordListResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPhotoView;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress;
import com.ssafy.yeogirok.domain.record.dto.StoryEditResponse;
import com.ssafy.yeogirok.domain.record.dto.StoryWriteResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitAddResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateResponse;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.service.RecordCompleteService;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.record.service.StoryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.record.service.VisitPhotoService;
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
	VisitCommandService visitCommandService;
	@MockitoBean
	StoryService storyService;
	@MockitoBean
	RecordCompleteService recordCompleteService;
	@MockitoBean
	VisitPhotoService visitPhotoService;
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
	class API_066 {

		static final String VALID_ITEM = "\"customPlace\": {\"name\": \" 경포대 \", \"latitude\": 37.795, \"longitude\": 128.9},"
			+ " \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\", \"order\": 1";

		@Test
		void 추가_응답은_201이고_검사한_값을_넘긴다() throws Exception {
			VisitAddCommand expected = new VisitAddCommand(3, List.of(new VisitAddCommand.Item(
				new VisitPlaceInput.CustomPlace("경포대", new BigDecimal("37.7950000"), new BigDecimal("128.9000000"), null),
				LocalDate.parse("2026-11-01"), Instant.parse("2026-11-01T00:30:00Z"), 1)));
			given(visitCommandService.add(USER_ID, 9L, expected))
				.willReturn(new VisitAddResponse(List.of(701L), 4, RecordStatus.DRAFT));

			mockMvc.perform(json(post("/api/v1/records/9/visits"), "{\"version\": 3, \"items\": [{" + VALID_ITEM + "}]}"))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
					{"success": true, "data": {"visitIds": [701], "version": 4, "status": "DRAFT"}}
					""", JsonCompareMode.STRICT));
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"items\": [{ITEM}]}|C001",
			"{\"version\": \"3\", \"items\": [{ITEM}]}|C002",
			"{\"version\": 3}|C001",
			"{\"version\": 3, \"items\": []}|C003",
			"{\"version\": 3, \"items\": {}}|C002",
			"{\"version\": 3, \"draftKey\": \"k\", \"items\": [{ITEM}]}|C003",
			"{\"version\": 3, \"items\": [{\"itemId\": 1, ITEM}]}|C003",
			"{\"version\": 3, \"items\": [{\"story\": \"x\", ITEM}]}|C003",
			"{\"version\": 3, \"items\": [{\"plannedItemId\": 5, ITEM}]}|C003",
			"{\"version\": 3, \"items\": [{\"placeRef\": {\"provider\": \"TOUR_API\", \"providerPlaceId\": \"1\"}, ITEM}]}|C003",
			"{\"version\": 3, \"items\": [{\"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\", \"order\": 1}]}|RE018",
			"{\"version\": 3, \"items\": [{\"placeRef\": {\"provider\": \"MANUAL\", \"providerPlaceId\": \"1\"}, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\", \"order\": 1}]}|C003",
			"{\"version\": 3, \"items\": [{\"customPlace\": {\"name\": \"  \", \"latitude\": 37, \"longitude\": 128}, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\", \"order\": 1}]}|P004",
			"{\"version\": 3, \"items\": [{\"customPlace\": {\"name\": \"a\", \"latitude\": 91, \"longitude\": 128}, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\", \"order\": 1}]}|C011",
			"{\"version\": 3, \"items\": [{\"customPlace\": {\"name\": \"a\", \"latitude\": 37, \"longitude\": 128}, \"visitedAt\": \"2026-11-01 09:30:00+09\", \"order\": 1}]}|C001",
			"{\"version\": 3, \"items\": [{\"customPlace\": {\"name\": \"a\", \"latitude\": 37, \"longitude\": 128}, \"visitDate\": \"2026-11-01\", \"visitedAt\": null, \"order\": 1}]}|C001",
			"{\"version\": 3, \"items\": [{\"customPlace\": {\"name\": \"a\", \"latitude\": 37, \"longitude\": 128}, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00\", \"order\": 1}]}|C002",
			"{\"version\": 3, \"items\": [{\"customPlace\": {\"name\": \"a\", \"latitude\": 37, \"longitude\": 128}, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-02 00:30:00+09\", \"order\": 1}]}|C012",
			"{\"version\": 3, \"items\": [{\"customPlace\": {\"name\": \"a\", \"latitude\": 37, \"longitude\": 128}, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\", \"order\": 0}]}|C003",
			"{\"version\": 3, \"items\": [{\"customPlace\": {\"name\": \"a\", \"latitude\": 37, \"longitude\": 128}, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\"}]}|C001"
		})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post("/api/v1/records/9/visits"), body.replace("ITEM", VALID_ITEM)))
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(visitCommandService);
		}

		@Test
		void 입력_오프셋_기준으로_날짜를_비교한다() throws Exception {
			given(visitCommandService.add(eq(USER_ID), eq(9L), any()))
				.willReturn(new VisitAddResponse(List.of(701L), 4, RecordStatus.DRAFT));
			String utcSameDay = VALID_ITEM.replace("2026-11-01 09:30:00+09", "2026-11-01 23:30:00Z");

			mockMvc.perform(json(post("/api/v1/records/9/visits"), "{\"version\": 3, \"items\": [{" + utcSameDay + "}]}"))
				.andExpect(status().isCreated());
		}
	}

	@Nested
	class API_067 {

		static final String ITEM = "\"visitId\": 701, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\"";

		@Test
		void 수정_응답과_검사한_값() throws Exception {
			VisitUpdateCommand expected = new VisitUpdateCommand(3, List.of(new VisitUpdateCommand.Item(701L, null,
				LocalDate.parse("2026-11-01"), Instant.parse("2026-11-01T00:30:00Z"), 2)), List.of(702L, 703L), true);
			given(visitCommandService.update(USER_ID, 9L, expected))
				.willReturn(new VisitUpdateResponse(List.of(), 4, RecordStatus.DRAFT));

			mockMvc.perform(json(patch("/api/v1/records/9/visits"), "{\"version\": 3, \"items\": [{" + ITEM
					+ ", \"order\": 2}], \"removeVisitIds\": [702, 703, 702], \"confirmDeleteStories\": true}"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success": true, "data": {"actualVisits": [], "version": 4, "status": "DRAFT"}}
					""", JsonCompareMode.STRICT));
		}

		@Test
		void 삭제만_하면_items_는_빈_배열이어도_된다() throws Exception {
			given(visitCommandService.update(eq(USER_ID), eq(9L), any()))
				.willReturn(new VisitUpdateResponse(List.of(), 4, RecordStatus.DRAFT));

			mockMvc.perform(json(patch("/api/v1/records/9/visits"), "{\"version\": 3, \"items\": [], \"removeVisitIds\": [702]}"))
				.andExpect(status().isOk());
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"version\": 3}|C001",
			"{\"version\": 3, \"items\": [{\"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\"}]}|C001",
			"{\"version\": 3, \"items\": [{\"visitId\": \"a\", \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-11-01 09:30:00+09\"}]}|C002",
			"{\"version\": 3, \"items\": [{ITEM}, {ITEM}]}|C003",
			"{\"version\": 3, \"items\": [{ITEM}], \"removeVisitIds\": [701]}|C003",
			"{\"version\": 3, \"items\": [], \"removeVisitIds\": 701}|C002",
			"{\"version\": 3, \"items\": [], \"removeVisitIds\": [\"a\"]}|C002",
			"{\"version\": 3, \"items\": [], \"confirmDeleteStories\": \"true\"}|C002",
			"{\"version\": 3, \"items\": [{\"plannedItemId\": 1, ITEM}]}|C003",
			"{\"version\": 3, \"items\": [{\"order\": 0, ITEM}]}|C003",
			"{\"version\": 3, \"items\": [{\"visitId\": 701, \"visitDate\": \"2026-11-01\", \"visitedAt\": \"2026-10-31 23:00:00+09\"}]}|C012"
		})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(patch("/api/v1/records/9/visits"), body.replace("ITEM", ITEM)))
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(visitCommandService);
		}
	}

	@Nested
	class API_069_070 {

		@Test
		void 작성은_201이고_줄바꿈을_LF로_맞춰_넘긴다() throws Exception {
			given(storyService.write(USER_ID, 9L, 701L, "첫 줄\n둘째 줄\n셋째 줄"))
				.willReturn(new StoryWriteResponse(31L, USER_ID, "첫 줄\n둘째 줄\n셋째 줄", RecordStatus.DRAFT));

			mockMvc.perform(json(post("/api/v1/records/9/visits/701/stories"),
					"{\"body\": \"첫 줄\\r\\n둘째 줄\\r셋째 줄\"}"))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
					{"success": true, "data": {"storyId": 31, "authorId": 1, "body": "첫 줄\\n둘째 줄\\n셋째 줄",
					  "status": "DRAFT"}}
					""", JsonCompareMode.STRICT));
		}

		@Test
		void 수정_응답_모양() throws Exception {
			given(storyService.edit(USER_ID, 9L, 701L, 31L, "고친 글")).willReturn(
				new StoryEditResponse(31L, "고친 글", Instant.parse("2026-11-01T00:30:00Z"), RecordStatus.DRAFT));

			mockMvc.perform(json(patch("/api/v1/records/9/visits/701/stories/31"), "{\"body\": \"고친 글\"}"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success": true, "data": {"storyId": 31, "body": "고친 글",
					  "updatedAt": "2026-11-01 09:30:00+09", "status": "DRAFT"}}
					""", JsonCompareMode.STRICT));
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{}|C001",
			"{\"body\": null}|C001",
			"{\"body\": 3}|C002",
			"{\"body\": \"\"}|C002",
			"{\"body\": \" \\n \"}|C002"
		})
		void 본문_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post("/api/v1/records/9/visits/701/stories"), body))
				.andExpect(jsonPath("$.code").value(code));
			mockMvc.perform(json(patch("/api/v1/records/9/visits/701/stories/31"), body))
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(storyService);
		}
	}

	@Nested
	class API_068 {

		@Test
		void 연결_응답은_201이고_같은_ID는_한_번만_넘긴다() throws Exception {
			given(visitPhotoService.link(USER_ID, 9L, 701L, List.of(31L, 32L))).willReturn(new PhotoLinkResponse(
				List.of(new PhotoLinkResponse.Photo(31L, 701L, 1L), new PhotoLinkResponse.Photo(32L, 701L, null)), 4,
				RecordStatus.DRAFT));

			mockMvc.perform(json(post("/api/v1/records/9/visits/701/photos"), "{\"imageIds\": [31, 32, 31]}"))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
					{"success": true, "data": {"photos": [
					  {"imageId": 31, "visitId": 701, "uploadedBy": 1},
					  {"imageId": 32, "visitId": 701, "uploadedBy": null}
					], "version": 4, "status": "DRAFT"}}
					""", JsonCompareMode.STRICT));
		}

		@Test
		void 빈_배열은_그대로_넘긴다() throws Exception {
			given(visitPhotoService.link(USER_ID, 9L, 701L, List.of()))
				.willReturn(new PhotoLinkResponse(List.of(), 3, RecordStatus.DRAFT));

			mockMvc.perform(json(post("/api/v1/records/9/visits/701/photos"), "{\"imageIds\": []}"))
				.andExpect(status().isCreated());
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {"{}|C001", "{\"imageIds\": 31}|C002", "{\"imageIds\": [\"a\"]}|C002"})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post("/api/v1/records/9/visits/701/photos"), body))
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(visitPhotoService);
		}
	}

	@Nested
	class API_089 {

		@Test
		void 삭제_응답은_200() throws Exception {
			given(visitPhotoService.delete(USER_ID, 9L, 31L, 3))
				.willReturn(new PhotoDeleteResponse(9L, 31L, true, RecordStatus.DRAFT, 4));

			mockMvc.perform(delete("/api/v1/records/9/images/31").param("version", "3").with(asUser(USER_ID))
					.header("Idempotency-Key", "key-1"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success": true, "data": {"recordId": 9, "imageId": 31, "deleted": true, "status": "DRAFT",
					  "version": 4}}
					""", JsonCompareMode.STRICT));
		}

		@Test
		void version_이나_멱등키가_없으면_C001_version_형식_오류는_C002() throws Exception {
			mockMvc.perform(delete("/api/v1/records/9/images/31").with(asUser(USER_ID)).header("Idempotency-Key", "k"))
				.andExpect(jsonPath("$.code").value("C001"));
			mockMvc.perform(delete("/api/v1/records/9/images/31").param("version", "3").with(asUser(USER_ID)))
				.andExpect(jsonPath("$.code").value("C001"));
			mockMvc.perform(delete("/api/v1/records/9/images/31").param("version", "x").with(asUser(USER_ID))
					.header("Idempotency-Key", "k"))
				.andExpect(jsonPath("$.code").value("C002"));
			verifyNoInteractions(visitPhotoService);
		}
	}

	@Nested
	class API_071 {

		@Test
		void 완성_응답은_200() throws Exception {
			given(recordCompleteService.complete(USER_ID, 9L, 3))
				.willReturn(new RecordCompleteResponse(9L, RecordStatus.COMPLETED, 4));

			mockMvc.perform(json(post("/api/v1/records/9/complete"), "{\"version\": 3}"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success": true, "data": {"recordId": 9, "status": "COMPLETED", "version": 4}}
					""", JsonCompareMode.STRICT));
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {"{}|C001", "{\"version\": null}|C002", "{\"version\": 1.5}|C002"})
		void version_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post("/api/v1/records/9/complete"), body))
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(recordCompleteService);
		}
	}

	@Nested
	class API_058 {

		@Test
		void 목록_응답_모양과_쿼리_연결() throws Exception {
			given(recordQueryService.getList(USER_ID, 7L, RecordStatus.COMPLETED, "c1", 10)).willReturn(
				new RecordListResponse(List.of(new RecordListResponse.Item(9L, "가을 여행", 7L, RecordStatus.COMPLETED,
					LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"), "강문동", 1,
					List.of(new RecordListResponse.Member(1L, "여행자", null)), 2,
					new RecordProgress(RecordProgress.Stage.COMPLETED, null, null),
					new RecordListResponse.Publication(9L, Instant.parse("2026-11-03T01:00:00Z"), 4))), "next"));

			mockMvc.perform(get("/api/v1/records").with(asUser(USER_ID)).param("roomId", "7")
					.param("status", "COMPLETED").param("cursor", "c1").param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success": true, "data": {"items": [{
					  "id": 9, "title": "가을 여행", "roomId": 7, "status": "COMPLETED",
					  "startDate": "2026-11-01", "endDate": "2026-11-02", "regionName": "강문동", "memberCount": 1,
					  "members": [{"userId": 1, "nickname": "여행자", "profileImageUrl": null}],
					  "placeCount": 2, "progress": {"stage": "COMPLETED", "draftKey": null, "jobId": null},
					  "publication": {"courseId": 9, "publishedAt": "2026-11-03 10:00:00+09", "version": 4}
					}], "nextCursor": "next"}}
					""", JsonCompareMode.STRICT));
		}

		@ParameterizedTest
		@CsvSource({"status, DONE", "roomId, abc", "limit, x"})
		void 쿼리_형식_오류는_C002(String name, String value) throws Exception {
			mockMvc.perform(get("/api/v1/records").with(asUser(USER_ID)).param(name, value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C002"));
			verifyNoInteractions(recordQueryService);
		}
	}

	@Nested
	class API_059 {

		@Test
		void 상세_응답_모양() throws Exception {
			RecordPhotoView photo = new RecordPhotoView(31L, "https://signed/31", null, null, 701L);
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
					  "photoPool": [{"imageId": 31, "url": "https://signed/31", "attribution": null, "uploadedBy": null, "visitId": 701}],
					  "actualVisits": [{
					    "id": 701,
					    "place": {"provider": "MANUAL", "providerPlaceId": null, "name": "경포대",
					      "latitude": 37.7519000, "longitude": 128.8761000, "address": null, "categoryCode": null,
					      "imageUrl": null, "imageAttribution": null},
					    "plannedItemId": null, "visitDate": "2026-11-01", "visitedAt": "2026-11-01 09:30:00+09",
					    "order": 1,
					    "photos": [{"imageId": 31, "url": "https://signed/31", "attribution": null, "uploadedBy": null, "visitId": 701}],
					    "stories": [{"id": 41, "authorId": 2, "body": "좋았다"}]
					  }],
					  "materials": [], "publishedCourseId": null, "version": 3
					}}
					""", JsonCompareMode.STRICT));
		}
	}
}
