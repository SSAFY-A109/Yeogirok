package com.ssafy.yeogirok.domain.course.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ssafy.yeogirok.domain.course.dto.CourseDay;
import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CoursePreviewResponse;
import com.ssafy.yeogirok.domain.course.dto.CoursePublicationResponse;
import com.ssafy.yeogirok.domain.course.dto.MyCourseListResponse;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.course.dto.PublicPhoto;
import com.ssafy.yeogirok.domain.course.dto.PublicPlace;
import com.ssafy.yeogirok.domain.course.service.CoursePreviewService;
import com.ssafy.yeogirok.domain.course.service.CoursePublishService;
import com.ssafy.yeogirok.domain.course.service.MyCourseQueryService;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 코스 공개 HTTP 계약(경로·요청 형식 오류 코드·응답 모양). 값 규칙은 서비스 테스트가 본다. */
@WebMvcTest(CourseController.class)
@Import(WebMvcSecurity.class)
class CourseControllerTest {

	static final Long USER_ID = 1L;

	@Autowired
	MockMvc mockMvc;
	@MockitoBean
	CoursePreviewService coursePreviewService;
	@MockitoBean
	CoursePublishService coursePublishService;
	@MockitoBean
	MyCourseQueryService myCourseQueryService;
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
	class API_072 {

		static final String PATH = "/api/v1/records/7/publication-preview";

		@Test
		void 미리보기_응답_모양() throws Exception {
			PublicPlace place = new PublicPlace(PlaceProvider.MANUAL, null, "경포대", new BigDecimal("37.75"),
				new BigDecimal("128.87"), "강원 강릉시", "NATURE", "자연 힐링", null, null);
			given(coursePreviewService.preview(USER_ID, 7L, new CourseInput(3, "강릉 1박", null, true, null, null)))
				.willReturn(new CoursePreviewResponse(new CoursePreviewResponse.Preview("강릉 1박",
					List.of(new PublicAuthor(1L, null)), "설명", List.of(new CourseDay(1, List.of(new CourseDay.Item(
						11L, place, 1, List.of(new PublicPhoto(21L, "https://signed/p", null))))))), "abc",
					List.of(), List.of()));

			mockMvc.perform(json(post(PATH), "{\"version\": 3, \"title\": \" 강릉 1박 \", \"includePhotos\": true}"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success": true, "data": {
					  "preview": {"title": "강릉 1박", "authors": [{"id": 1, "profileImageUrl": null}],
					    "description": "설명",
					    "days": [{"day": 1, "items": [{"visitId": 11, "order": 1,
					      "place": {"provider": "MANUAL", "providerPlaceId": null, "name": "경포대", "latitude": 37.75,
					        "longitude": 128.87, "address": "강원 강릉시", "categoryCode": "NATURE",
					        "categoryName": "자연 힐링", "imageUrl": null, "imageAttribution": null},
					      "photos": [{"imageId": 21, "url": "https://signed/p", "attribution": null}]}]}]},
					  "selectionHash": "abc", "excluded": [], "warnings": []}}
					""", org.springframework.test.json.JsonCompareMode.STRICT));
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"title\": \"t\", \"includePhotos\": true}|C001",
			"{\"version\": \"1\", \"title\": \"t\", \"includePhotos\": true}|C002",
			"{\"version\": 1, \"includePhotos\": true}|C001",
			"{\"version\": 1, \"title\": null, \"includePhotos\": true}|C001",
			"{\"version\": 1, \"title\": 3, \"includePhotos\": true}|C002",
			"{\"version\": 1, \"title\": \"  \", \"includePhotos\": true}|C003",
			"{\"version\": 1, \"title\": \"t\"}|C001",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": null}|C001",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": \"true\"}|C002",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": true, \"description\": null}|C002",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": true, \"description\": 1}|C002",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": true, \"visitIds\": [1]}|C003",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": true, \"photoIds\": null}|C003",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": true, \"storyIds\": []}|C003",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": true, \"visibility\": \"PUBLIC\"}|C003",
			"{\"version\": 1, \"title\": \"t\", \"includePhotos\": true, \"publishedAt\": \"x\"}|C003"
		})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post(PATH), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(coursePreviewService);
		}

		@Test
		void 설명_길이는_2000자까지() throws Exception {
			given(coursePreviewService.preview(anyLong(), anyLong(), any())).willReturn(
				new CoursePreviewResponse(new CoursePreviewResponse.Preview("t", List.of(), "", List.of()), "h",
					List.of(), List.of()));
			String body = "{\"version\": 1, \"title\": \"t\", \"includePhotos\": true, \"description\": \"%s\"}";

			mockMvc.perform(json(post(PATH), body.formatted("가".repeat(2000)))).andExpect(status().isOk());
			mockMvc.perform(json(post(PATH), body.formatted("가".repeat(2001))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("C003"));
		}
	}

	@Nested
	class API_073 {

		static final String PATH = "/api/v1/records/7/publications";
		static final String BODY = "{\"version\": 3, \"title\": \"강릉 1박\", \"includePhotos\": false, "
			+ "\"selectionHash\": \"abc\"}";

		@Test
		void 게시하면_201_과_courseId_publishedAt_version() throws Exception {
			given(coursePublishService.publish(USER_ID, 7L, new CourseInput(3, "강릉 1박", null, false, "abc", null)))
				.willReturn(new CoursePublicationResponse(7L, Instant.parse("2026-11-11T01:00:00Z"), 4));

			mockMvc.perform(json(post(PATH), BODY))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
					{"success": true, "data": {"courseId": 7, "publishedAt": "2026-11-11 10:00:00+09", "version": 4}}
					""", org.springframework.test.json.JsonCompareMode.STRICT));
		}

		@Test
		void 멱등키는_기록_ID_에_건다() throws Exception {
			given(coursePublishService.publish(anyLong(), anyLong(), any()))
				.willReturn(new CoursePublicationResponse(7L, Instant.parse("2026-11-11T01:00:00Z"), 4));

			mockMvc.perform(json(post(PATH), BODY));

			ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
			verify(idempotencyService).execute(key.capture(), any(), any(), any(), any());
			assertThat(key.getValue().scope()).isEqualTo(CourseController.PUBLISH_SCOPE);
			assertThat(key.getValue().target()).isEqualTo("7");
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"{\"version\": 3, \"title\": \"t\", \"includePhotos\": false}|C001",
			"{\"version\": 3, \"title\": \"t\", \"includePhotos\": false, \"selectionHash\": null}|C001",
			"{\"version\": 3, \"title\": \"t\", \"includePhotos\": false, \"selectionHash\": 1}|C002",
			"{\"title\": \"t\", \"includePhotos\": false, \"selectionHash\": \"abc\"}|C001",
			"{\"version\": 3, \"title\": \"t\", \"includePhotos\": false, \"selectionHash\": \"abc\", \"visitIds\": [1]}|C003"
		})
		void 요청_형식_오류(String body, String code) throws Exception {
			mockMvc.perform(json(post(PATH), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(code));
			verifyNoInteractions(coursePublishService, idempotencyService);
		}
	}

	@Nested
	class API_074 {

		@Test
		void 목록_응답_모양과_쿼리_전달() throws Exception {
			given(myCourseQueryService.getMine(USER_ID, "c1", 10)).willReturn(new MyCourseListResponse(List.of(
				new MyCourseListResponse.Item(7L, "강릉 1박", List.of(new PublicAuthor(1L, null)),
					Instant.parse("2026-11-11T01:00:00Z"), 4)), "next"));

			mockMvc.perform(get("/api/v1/users/me/courses").param("cursor", "c1").param("limit", "10")
					.with(asUser(USER_ID)))
				.andExpect(status().isOk())
				.andExpect(content().json("""
					{"success": true, "data": {"items": [{"courseId": 7, "title": "강릉 1박",
					  "authors": [{"id": 1, "profileImageUrl": null}], "publishedAt": "2026-11-11 10:00:00+09",
					  "version": 4}], "nextCursor": "next"}}
					""", org.springframework.test.json.JsonCompareMode.STRICT));
		}
	}
}
