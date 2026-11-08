package com.ssafy.yeogirok.domain.tip.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ssafy.yeogirok.domain.tip.dto.TipReactionRequest.TipReactionCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipReactionResponse;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchRequest.TipSearchQuery;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.SpatialReference;
import com.ssafy.yeogirok.domain.tip.entity.TipReaction;
import com.ssafy.yeogirok.domain.tip.service.TipReactionService;
import com.ssafy.yeogirok.domain.tip.service.TipSearchService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** AR 팁 HTTP 계약(요청 형식 오류 코드, 응답 모양). 값 규칙은 각 서비스 테스트가 본다. */
@WebMvcTest(TipController.class)
@Import(WebMvcSecurity.class)
class TipControllerTest {

	static final Long USER_ID = 1L;
	static final String LOCATION =
		"{\"latitude\":37.5012345,\"longitude\":127.0398765,\"accuracyMeters\":8,\"capturedAt\":\"2026-11-08 12:00:00+09\"}";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	TipSearchService tipSearchService;

	@MockitoBean
	TipReactionService tipReactionService;

	ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.with(asUser(USER_ID)).contentType(MediaType.APPLICATION_JSON)
			.content(body.replace("LOC", LOCATION)));
	}

	static void expectError(ResultActions result, int status, String code) throws Exception {
		result.andExpect(status().is(status))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value(code));
	}

	@Nested
	class API_047_주변_탐색 {

		@Test
		void 앱이_검사하는_응답_모양을_돌려준다() throws Exception {
			given(tipSearchService.search(any())).willReturn(new TipSearchResponse(List.of(new TipSearchResponse.Item(
				7L, ArTipCategory.RESTAURANT, new BigDecimal("37.5012345"), new BigDecimal("127.0398765"), "창가 자리가 좋아요",
				new SpatialReference(SpatialReference.PROVIDER, "anchor-1", Instant.parse("2026-11-09T03:00:00Z")),
				Map.of("schemaVersion", 1), 4.5, true)), null, 50, 1, 5));

			perform(post("/api/v1/tips/search"), "{\"location\":LOC,\"category\":\"RESTAURANT\",\"limit\":10,\"cursor\":\"x\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[0].id").value(7))
				.andExpect(jsonPath("$.data.items[0].category").value("RESTAURANT"))
				.andExpect(jsonPath("$.data.items[0].latitude").value(37.5012345))
				.andExpect(jsonPath("$.data.items[0].summary").value("창가 자리가 좋아요"))
				.andExpect(jsonPath("$.data.items[0].spatialReference.provider").value("ARCORE_CLOUD_ANCHOR"))
				.andExpect(jsonPath("$.data.items[0].spatialReference.referenceId").value("anchor-1"))
				.andExpect(jsonPath("$.data.items[0].spatialReference.expiresAt").value("2026-11-09 12:00:00+09"))
				.andExpect(jsonPath("$.data.items[0].placement.schemaVersion").value(1))
				.andExpect(jsonPath("$.data.items[0].distanceMeters").value(4.5))
				.andExpect(jsonPath("$.data.items[0].canInteract").value(true))
				.andExpect(jsonPath("$.data.nextCursor").hasJsonPath())
				.andExpect(jsonPath("$.data.nextCursor").isEmpty())
				.andExpect(jsonPath("$.data.appliedRadiusMeters").value(50))
				.andExpect(jsonPath("$.data.totalCount").value(1))
				.andExpect(jsonPath("$.data.interactionRadiusMeters").value(5));

			ArgumentCaptor<TipSearchQuery> query = ArgumentCaptor.forClass(TipSearchQuery.class);
			verify(tipSearchService).search(query.capture());
			assertThat(query.getValue().category()).isEqualTo(ArTipCategory.RESTAURANT);
			assertThat(query.getValue().limit()).isEqualTo(10L);
			assertThat(query.getValue().location().accuracyMeters()).isEqualByComparingTo("8");
		}

		@ParameterizedTest(name = "{0} → {1}")
		@CsvSource(delimiter = '|', textBlock = """
			{}                                                    | 422 | C008
			{"location":{"latitude":37.5,"longitude":127.0,"capturedAt":"2026-11-08 12:00:00+09"}} | 422 | C008
			{"location":"here"}                                   | 400 | C002
			{"location":LOC,"radiusMeters":"50"}                  | 400 | C002
			{"location":LOC,"limit":1.5}                          | 400 | C002
			{"location":LOC,"category":1}                         | 400 | C002
			{"location":LOC,"cursor":1}                           | 400 | C002
			{"location":LOC,"category":"FOOD"}                    | 400 | C003
			{"location":LOC,"category":""}                        | 400 | C003
			""")
		void 요청_형식_오류(String body, int status, String code) throws Exception {
			expectError(perform(post("/api/v1/tips/search"), body), status, code);
			verifyNoInteractions(tipSearchService);
		}

		@Test
		void 로그인하지_않으면_A001() throws Exception {
			mockMvc.perform(post("/api/v1/tips/search").contentType(MediaType.APPLICATION_JSON)
					.content("{\"location\":" + LOCATION + "}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("A001"));
		}
	}

	@Nested
	class API_052_반응 {

		@Test
		void 취소하면_myReaction_null과_집계를_돌려준다() throws Exception {
			given(tipReactionService.react(eq(USER_ID), eq(7L), any())).willReturn(new TipReactionResponse(7L, null, 3, 1));

			perform(put("/api/v1/tips/7/reaction"), "{\"reaction\":\"NONE\",\"location\":LOC}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tipId").value(7))
				.andExpect(jsonPath("$.data.myReaction").hasJsonPath())
				.andExpect(jsonPath("$.data.myReaction").isEmpty())
				.andExpect(jsonPath("$.data.likes").value(3))
				.andExpect(jsonPath("$.data.dislikes").value(1));

			ArgumentCaptor<TipReactionCommand> command = ArgumentCaptor.forClass(TipReactionCommand.class);
			verify(tipReactionService).react(eq(USER_ID), eq(7L), command.capture());
			assertThat(command.getValue().reaction()).isNull();
		}

		@Test
		void LIKE는_그대로_넘긴다() throws Exception {
			given(tipReactionService.react(eq(USER_ID), eq(7L), any()))
				.willReturn(new TipReactionResponse(7L, TipReaction.LIKE, 1, 0));

			perform(put("/api/v1/tips/7/reaction"), "{\"reaction\":\"LIKE\",\"location\":LOC}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.myReaction").value("LIKE"));
		}

		@ParameterizedTest(name = "{0} → {1}")
		@CsvSource(delimiter = '|', textBlock = """
			{"location":LOC}                       | 400 | C001
			{"reaction":null,"location":LOC}       | 400 | C001
			{"reaction":1,"location":LOC}          | 400 | C002
			{"reaction":"like","location":LOC}     | 400 | C003
			{"reaction":"TOGGLE","location":LOC}   | 400 | C003
			{"reaction":"LIKE"}                    | 422 | C008
			""")
		void 요청_형식_오류(String body, int status, String code) throws Exception {
			expectError(perform(put("/api/v1/tips/7/reaction"), body), status, code);
			verifyNoInteractions(tipReactionService);
		}
	}
}
