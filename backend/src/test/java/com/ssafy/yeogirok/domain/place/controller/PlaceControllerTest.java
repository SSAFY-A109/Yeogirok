package com.ssafy.yeogirok.domain.place.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.yeogirok.domain.place.dto.PlaceSearchResponse;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.service.PlaceSearchService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** API-026 HTTP 계약. 조건 검사는 PlaceSearchServiceTest 가 본다. */
@WebMvcTest(PlaceController.class)
@Import(WebMvcSecurity.class)
class PlaceControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PlaceSearchService service;

	@Test
	void 검색_결과를_명세의_모양으로_준다() throws Exception {
		given(service.search("경포", "51150", "Mg", 10)).willReturn(new PlaceSearchResponse(List.of(
			new PlaceSearchResponse.Item(PlaceProvider.TOUR_API, "126508", "경포해변", new BigDecimal("37.8055"),
				new BigDecimal("128.9087"), "강릉시 창해로 514", "NATURE", "http://img/1.jpg", "한국관광공사")),
			"Mw", 45));

		mockMvc.perform(get("/api/v1/places").with(asUser(1L))
				.param("q", "경포").param("regionCode", "51150").param("cursor", "Mg").param("limit", "10"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {
				  "items": [{"provider": "TOUR_API", "providerPlaceId": "126508", "name": "경포해변",
				    "latitude": 37.8055, "longitude": 128.9087, "address": "강릉시 창해로 514",
				    "categoryCode": "NATURE", "imageUrl": "http://img/1.jpg", "imageAttribution": "한국관광공사"}],
				  "nextCursor": "Mw", "totalCount": 45}}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 선택_조건을_빼면_null_로_넘긴다() throws Exception {
		given(service.search("카페", null, null, null)).willReturn(new PlaceSearchResponse(List.of(), null, 0));

		mockMvc.perform(get("/api/v1/places").with(asUser(1L)).param("q", "카페"))
			.andExpect(status().isOk());
		verify(service).search("카페", null, null, null);
	}

	@Test
	void 검색어_오류는_400_P002_TourAPI_실패는_502_P003() throws Exception {
		given(service.search(null, null, null, null))
			.willThrow(new BusinessException(ExceptionType.PLACE_SEARCH_CONDITION_INVALID));
		given(service.search("카페", null, null, null))
			.willThrow(new BusinessException(ExceptionType.PLACE_PROVIDER_UNAVAILABLE));

		mockMvc.perform(get("/api/v1/places").with(asUser(1L)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("P002"));
		mockMvc.perform(get("/api/v1/places").with(asUser(1L)).param("q", "카페"))
			.andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("P003"));
	}

	@Test
	void limit_이_정수가_아니면_C002() throws Exception {
		mockMvc.perform(get("/api/v1/places").with(asUser(1L)).param("q", "카페").param("limit", "many"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C002"));
		verifyNoInteractions(service);
	}

	@Test
	void 토큰이_없으면_A001() throws Exception {
		mockMvc.perform(get("/api/v1/places").param("q", "카페"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}
}
