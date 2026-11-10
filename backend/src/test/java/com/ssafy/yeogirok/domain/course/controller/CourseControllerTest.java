package com.ssafy.yeogirok.domain.course.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.course.dto.PublicPhoto;
import com.ssafy.yeogirok.domain.course.dto.PublicPlace;
import com.ssafy.yeogirok.domain.course.service.CoursePreviewService;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
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
}
