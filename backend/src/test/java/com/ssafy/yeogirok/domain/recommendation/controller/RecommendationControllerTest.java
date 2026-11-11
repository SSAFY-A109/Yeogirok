package com.ssafy.yeogirok.domain.recommendation.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ssafy.yeogirok.domain.recommendation.RecommendationPreferences;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationCommand;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRef;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceSaveCommand;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationMode;
import com.ssafy.yeogirok.domain.recommendation.service.PlaceRecommendationService;
import com.ssafy.yeogirok.domain.recommendation.service.RecommendationPreferenceService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 추천 조건·추천 HTTP 계약(경로·요청 형식 오류 코드·응답 모양·멱등키 연결). 값 규칙은 서비스 테스트가 본다. */
@WebMvcTest(RecommendationController.class)
@Import(WebMvcSecurity.class)
class RecommendationControllerTest {

	static final Long USER_ID = 1L;

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RecommendationPreferenceService preferenceService;

	@MockitoBean
	PlaceRecommendationService recommendationService;

	@MockitoBean
	IdempotencyService idempotencyService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runActionThroughIdempotency() {
		given(idempotencyService.execute(any(), any(), any(), any(), any()))
			.willAnswer(invocation -> ((Supplier<Object>)invocation.getArgument(2)).get());
	}

	private ResultActions putPreferences(String json) throws Exception {
		return mockMvc.perform(put("/api/v1/rooms/7/preferences").with(asUser(USER_ID))
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/* ── API-081·033 ────────────────────────────────────────── */

	@Test
	void 조건_조회_응답_모양() throws Exception {
		given(preferenceService.get(USER_ID, 7L)).willReturn(PreferenceResponse.of(7L, RecommendationPreferences.EMPTY));

		mockMvc.perform(get("/api/v1/rooms/7/preferences").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"roomId": 7,
				  "preferences": {"regionCode": null, "categoryCodes": [], "skipped": false}, "version": 1}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 조건을_저장하면_중복_카테고리를_합쳐_넘긴다() throws Exception {
		given(preferenceService.save(eq(USER_ID), eq(7L), any())).willReturn(PreferenceResponse.of(7L,
			new RecommendationPreferences("51150", List.of("CAFE", "NATURE"), 2)));

		putPreferences("""
			{"version": 1, "regionCode": "51150", "categoryCodes": ["CAFE", "NATURE", "CAFE"], "skipped": false}
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.version").value(2))
			.andExpect(jsonPath("$.data.preferences.categoryCodes[1]").value("NATURE"));
		verify(preferenceService).save(USER_ID, 7L, new PreferenceSaveCommand(1, "51150", List.of("CAFE", "NATURE")));
	}

	@Test
	void skipped_를_생략해도_저장한다() throws Exception {
		given(preferenceService.save(eq(USER_ID), eq(7L), any())).willReturn(PreferenceResponse.of(7L,
			new RecommendationPreferences("51150", List.of("CAFE"), 2)));

		putPreferences("""
			{"version": 1, "regionCode": "51150", "categoryCodes": ["CAFE"]}
			""").andExpect(status().isOk());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"regionCode\":\"51150\",\"categoryCodes\":[\"CAFE\"]}|C001",
		"{\"version\":1,\"categoryCodes\":[\"CAFE\"]}|C001",
		"{\"version\":1,\"regionCode\":null,\"categoryCodes\":[\"CAFE\"]}|C001",
		"{\"version\":1,\"regionCode\":\"51150\"}|C001",
		"{\"version\":\"1\",\"regionCode\":\"51150\",\"categoryCodes\":[\"CAFE\"]}|C002",
		"{\"version\":1,\"regionCode\":51150,\"categoryCodes\":[\"CAFE\"]}|C002",
		"{\"version\":1,\"regionCode\":\"51150\",\"categoryCodes\":\"CAFE\"}|C002",
		"{\"version\":1,\"regionCode\":\"51150\",\"categoryCodes\":[1]}|C002",
		"{\"version\":1,\"regionCode\":\"51150\",\"categoryCodes\":[\"CAFE\"],\"skipped\":\"no\"}|C002",
		"{\"version\":1,\"regionCode\":\"51\",\"categoryCodes\":[\"CAFE\"]}|C003",
		"{\"version\":1,\"regionCode\":\"5115A\",\"categoryCodes\":[\"CAFE\"]}|C003",
		"{\"version\":1,\"regionCode\":\"51150\",\"categoryCodes\":[]}|C003",
		"{\"version\":1,\"regionCode\":\"51150\",\"categoryCodes\":[\"cafe\"]}|C003",
		"{\"version\":1,\"regionCode\":\"51150\",\"categoryCodes\":[\"LODGING\"]}|C003",
		"{\"version\":1,\"regionCode\":\"51150\",\"categoryCodes\":[\"CAFE\"],\"skipped\":true}|C003"
	})
	void 조건_요청_형식_오류(String json, String code) throws Exception {
		putPreferences(json)
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(preferenceService);
	}

	/* ── API-034 ────────────────────────────────────────────── */

	@Test
	void 추천_응답_모양과_방_단위_멱등키() throws Exception {
		given(recommendationService.recommend(eq(USER_ID), eq(7L), any())).willReturn(new PlaceRecommendationResponse(
			31L, RecommendationMode.PERSONALIZED, List.of(new PlaceRecommendationResponse.Item(1,
				new PlaceRecommendationResponse.RecommendedPlace("TOUR_API", "126508", "경포해변",
					new BigDecimal("37.8055"), new BigDecimal("128.9087"), "강릉시 창해로 514", "NATURE",
					"http://img/1.jpg", "한국관광공사"),
				"자연 힐링 · 담은 장소들과 1.2km", List.of("카테고리 일치: 자연 힐링", "담은 장소들과 1.2km"))),
			Instant.parse("2026-11-11T00:00:00Z")));

		mockMvc.perform(post("/api/v1/rooms/7/place-recommendations").with(asUser(USER_ID))
				.header("Idempotency-Key", "key-1"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {
				  "recommendationRequestId": 31, "mode": "PERSONALIZED",
				  "items": [{"rank": 1,
				    "place": {"provider": "TOUR_API", "providerPlaceId": "126508", "name": "경포해변",
				      "latitude": 37.8055, "longitude": 128.9087, "address": "강릉시 창해로 514",
				      "categoryCode": "NATURE", "imageUrl": "http://img/1.jpg", "imageAttribution": "한국관광공사"},
				    "reason": "자연 힐링 · 담은 장소들과 1.2km",
				    "reasonEvidence": ["카테고리 일치: 자연 힐링", "담은 장소들과 1.2km"]}],
				  "generatedAt": "2026-11-11 09:00:00+09"}}
				""", JsonCompareMode.STRICT));

		ArgumentCaptor<IdempotencyKey> key = ArgumentCaptor.forClass(IdempotencyKey.class);
		verify(idempotencyService).execute(key.capture(), eq(PlaceRecommendationCommand.EMPTY), any(), any(), any());
		assertThat(key.getValue()).isEqualTo(new IdempotencyKey(USER_ID, RecommendationController.RECOMMENDATION_SCOPE,
			"7", "key-1"));
	}

	@Test
	void 추천_본문을_읽어_넘긴다() throws Exception {
		mockMvc.perform(post("/api/v1/rooms/7/place-recommendations").with(asUser(USER_ID))
				.header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"limit": 5, "excludedPlaces": [{"provider": "TOUR_API", "providerPlaceId": "126508"}]}
					"""))
			.andExpect(status().isOk());

		verify(recommendationService).recommend(USER_ID, 7L,
			new PlaceRecommendationCommand(5, List.of(new PlaceRef("TOUR_API", "126508"))));
	}

	@Test
	void 멱등키가_없으면_실패하고_추천하지_않는다() throws Exception {
		mockMvc.perform(post("/api/v1/rooms/7/place-recommendations").with(asUser(USER_ID)))
			.andExpect(status().is4xxClientError());
		verifyNoInteractions(recommendationService);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"{\"limit\":\"5\"}|C002",
		"{\"limit\":1.5}|C002",
		"{\"excludedPlaces\":{}}|C002",
		"{\"excludedPlaces\":[\"126508\"]}|C002",
		"{\"excludedPlaces\":[{\"provider\":\"TOUR_API\"}]}|C001",
		"{\"excludedPlaces\":[{\"provider\":\"MANUAL\",\"providerPlaceId\":\"1\"}]}|C003",
		"{\"excludedPlaces\":[{\"provider\":\"TOUR_API\",\"providerPlaceId\":\" \"}]}|C003"
	})
	void 추천_요청_형식_오류(String json, String code) throws Exception {
		mockMvc.perform(post("/api/v1/rooms/7/place-recommendations").with(asUser(USER_ID))
				.header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(jsonPath("$.code").value(code));
		verifyNoInteractions(recommendationService);
	}
}
