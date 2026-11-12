package com.ssafy.yeogirok.domain.course.controller;

import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.ssafy.yeogirok.domain.course.dto.ImportCountBasis;
import com.ssafy.yeogirok.domain.course.dto.PopularPlaceListResponse;
import com.ssafy.yeogirok.domain.course.service.PopularPlaceService;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.room.dto.PlaceInfo;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** 200 API-093 HTTP 계약(경로·쿼리 전달·응답 모양). 규칙은 PopularPlaceServiceTest 가 본다. */
@WebMvcTest(MainController.class)
@Import(WebMvcSecurity.class)
class MainControllerTest {

	static final Long USER_ID = 1L;

	@Autowired
	MockMvc mockMvc;
	@MockitoBean
	PopularPlaceService popularPlaceService;

	@Test
	void 인기_장소_응답_모양과_카테고리_전달() throws Exception {
		given(popularPlaceService.popular("NATURE")).willReturn(new PopularPlaceListResponse(List.of(
			new PopularPlaceListResponse.Item(1, new PlaceInfo(PlaceProvider.TOUR_API, "12345", "경포해변",
				new BigDecimal("37.805"), new BigDecimal("128.907"), null, "NATURE", "자연 힐링", "5115010100", "강문동",
				null, null), 198)),
			new ImportCountBasis("IMPORT_COUNT_30D", Instant.parse("2026-10-12T03:00:00Z"),
				Instant.parse("2026-11-11T03:00:00Z"))));

		mockMvc.perform(get("/api/v1/main/popular-places").param("category", "NATURE").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"success": true, "data": {"items": [{"rank": 1, "place": {"provider": "TOUR_API",
				  "providerPlaceId": "12345", "name": "경포해변", "latitude": 37.805, "longitude": 128.907, "address": null,
				  "categoryCode": "NATURE", "categoryName": "자연 힐링", "regionCode": "5115010100", "regionName": "강문동",
				  "imageUrl": null, "imageAttribution": null}, "importCount30d": 198}],
				  "basis": {"type": "IMPORT_COUNT_30D", "periodStart": "2026-10-12 12:00:00+09",
				    "periodEnd": "2026-11-11 12:00:00+09"}}}
				""", JsonCompareMode.STRICT));
	}
}
