package com.ssafy.yeogirok.domain.statistics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static com.ssafy.yeogirok.support.WebMvcSecurity.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.WebMvcSecurity;

/** API-046 HTTP 계약. 집계 규칙은 VisitStatisticsServiceTest 가 본다. */
@WebMvcTest(VisitStatisticsController.class)
@Import(WebMvcSecurity.class)
class VisitStatisticsControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	VisitStatisticsService service;

	@Test
	void 명세의_모양으로_응답하고_개인_정보는_담지_않는다() throws Exception {
		Map<String, Long> categoryCounts = new LinkedHashMap<>();
		categoryCounts.put("FOOD", 1L);
		categoryCounts.put("CAFE", 2L);
		given(service.getStatistics("126.9,37.4,127.1,37.6", 15.5, "CAFE", LocalDate.parse("2026-11-01"),
			LocalDate.parse("2026-11-04"), "VISIT_COUNT")).willReturn(new VisitStatisticsResponse(
			"VISIT_COUNT",
			new VisitStatisticsResponse.Period(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-04")),
			new VisitStatisticsResponse.Aggregation("LAT_LNG_GRID", "grid-0.005-v1", "0.005deg", 1),
			List.of(new VisitStatisticsResponse.Cell("1:7501:25401",
				new VisitStatisticsResponse.Center(new BigDecimal("37.5025000"), new BigDecimal("127.0025000")),
				2, categoryCounts)),
			Instant.parse("2026-11-04T05:00:00Z")));

		getAs("/api/v1/visit-statistics?bbox=126.9,37.4,127.1,37.6&zoom=15.5&category=CAFE"
			+ "&from=2026-11-01&to=2026-11-04&metric=VISIT_COUNT")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": {
				    "metric": "VISIT_COUNT",
				    "period": { "from": "2026-11-01", "to": "2026-11-04" },
				    "aggregation": { "unit": "LAT_LNG_GRID", "version": "grid-0.005-v1", "precision": "0.005deg", "minCount": 1 },
				    "cells": [
				      { "cellId": "1:7501:25401", "center": { "latitude": 37.5025000, "longitude": 127.0025000 },
				        "count": 2, "categoryCounts": { "FOOD": 1, "CAFE": 2 } }
				    ],
				    "updatedAt": "2026-11-04 14:00:00+09"
				  }
				}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 기간을_생략하면_period는_null이고_metric은_넘기지_않는다() throws Exception {
		given(service.getStatistics(eq("126.9,37.4,127.1,37.6"), eq(10.0), isNull(), isNull(), isNull(), isNull()))
			.willReturn(new VisitStatisticsResponse("VISIT_COUNT", new VisitStatisticsResponse.Period(null, null),
				new VisitStatisticsResponse.Aggregation("LAT_LNG_GRID", "v", "0.04deg", 1), List.of(),
				Instant.parse("2026-11-04T05:00:00Z")));

		getAs("/api/v1/visit-statistics?bbox=126.9,37.4,127.1,37.6&zoom=10")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.period.from").value((Object)null))
			.andExpect(jsonPath("$.data.period.to").value((Object)null))
			.andExpect(jsonPath("$.data.cells").isEmpty());
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		bbox 생략       | zoom=10                         | C001
		zoom 생략       | bbox=126.9,37.4,127.1,37.6      | C001
		zoom 숫자 아님  | bbox=126.9,37.4,127.1,37.6&zoom=a | C002
		날짜 형식 틀림  | bbox=126.9,37.4,127.1,37.6&zoom=10&from=2026/11/01 | C002
		""")
	void 쿼리가_없거나_타입이_틀리면_400(String name, String query, String code) throws Exception {
		getAs("/api/v1/visit-statistics?" + query)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		UNSUPPORTED_METRIC | ST001
		INVALID_BBOX       | C013
		INVALID_DATE_RANGE | C012
		NOT_ALLOWED_VALUE  | C003
		""")
	void 서비스_오류는_명세의_코드로_응답한다(ExceptionType type, String code) throws Exception {
		given(service.getStatistics(any(), anyDouble(), any(), any(), any(), any()))
			.willThrow(new BusinessException(type));

		getAs("/api/v1/visit-statistics?bbox=126.9,37.4,127.1,37.6&zoom=10")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
	}

	@Test
	void 인증_정보가_없으면_401_A001() throws Exception {
		mockMvc.perform(get("/api/v1/visit-statistics?bbox=126.9,37.4,127.1,37.6&zoom=10"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}

	private ResultActions getAs(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser(1L)));
	}
}
