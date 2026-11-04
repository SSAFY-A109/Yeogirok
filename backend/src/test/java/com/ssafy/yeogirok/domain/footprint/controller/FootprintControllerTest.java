package com.ssafy.yeogirok.domain.footprint.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintListResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateResponse;
import com.ssafy.yeogirok.domain.footprint.service.FootprintService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.security.PermitAllSecurityConfig;

/**
 * HTTP 계약(경로·응답 모양·오류 코드). 규칙 자체는 FootprintServiceTest·FootprintFinderTest 가 본다.
 *
 * <p>@WebMvcTest 는 일반 @Configuration 을 읽지 않으므로 지금 앱이 쓰는 보안 설정을 직접 가져온다.
 * 회원 기능의 SecurityConfig 로 바뀌면 이 Import 도 바꾼다.
 */
@WebMvcTest(FootprintController.class)
@Import(PermitAllSecurityConfig.class)
class FootprintControllerTest {

	static final Long USER_ID = 1L;

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	FootprintService footprintService;

	@Test
	void API_043_상세는_명세의_필드와_datetime_형식으로_응답한다() throws Exception {
		given(footprintService.getFootprint(USER_ID, 10L)).willReturn(new FootprintDetailResponse(
			10L, new BigDecimal("37.5012345"), new BigDecimal("127.0398765"), "역삼동", "서울특별시 강남구 역삼동 123",
			"좋았다\n또 올게", List.of("CAFE", "FOOD"),
			Instant.parse("2026-11-04T03:00:00Z"), Instant.parse("2026-11-04T03:00:05Z"), 1));

		getAs(USER_ID, "/api/v1/footprints/10")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": {
				    "id": 10,
				    "latitude": 37.5012345,
				    "longitude": 127.0398765,
				    "locationName": "역삼동",
				    "address": "서울특별시 강남구 역삼동 123",
				    "body": "좋았다\\n또 올게",
				    "categoryCodes": ["CAFE", "FOOD"],
				    "visitedAt": "2026-11-04 12:00:00+09",
				    "createdAt": "2026-11-04 12:00:05+09",
				    "version": 1
				  }
				}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 위치명_주소_본문이_없으면_null로_내보낸다() throws Exception {
		given(footprintService.getFootprint(USER_ID, 10L)).willReturn(new FootprintDetailResponse(
			10L, new BigDecimal("37.5"), new BigDecimal("127.0"), null, null, null, List.of("NATURE"),
			Instant.parse("2026-11-04T03:00:00Z"), Instant.parse("2026-11-04T03:00:00Z"), 1));

		getAs(USER_ID, "/api/v1/footprints/10")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.locationName").value((Object)null))
			.andExpect(jsonPath("$.data.address").value((Object)null))
			.andExpect(jsonPath("$.data.body").value((Object)null))
			.andExpect(jsonPath("$.data.length()").value(10));
	}

	@Test
	void 다른_사람_발자취는_403_F002() throws Exception {
		given(footprintService.getFootprint(USER_ID, 10L))
			.willThrow(new BusinessException(ExceptionType.FOOTPRINT_NOT_OWNER));

		getAs(USER_ID, "/api/v1/footprints/10")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("F002"));
	}

	@Test
	void 없거나_삭제한_발자취는_404_F001() throws Exception {
		given(footprintService.getFootprint(USER_ID, 10L))
			.willThrow(new BusinessException(ExceptionType.FOOTPRINT_NOT_FOUND));

		getAs(USER_ID, "/api/v1/footprints/10")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("F001"));
	}

	@Test
	void 인증_정보가_없으면_401_A001() throws Exception {
		mockMvc.perform(get("/api/v1/footprints/10"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}

	@Test
	void 발자취_ID가_숫자가_아니면_400_C002() throws Exception {
		getAs(USER_ID, "/api/v1/footprints/abc")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C002"));
	}

	/* ── API-042 내 발자취 목록 ───────────────────────────────── */

	@Test
	void API_042_목록은_items와_nextCursor로_응답하고_쿼리를_그대로_넘긴다() throws Exception {
		given(footprintService.getMyFootprints(USER_ID, "126.9,37.4,127.1,37.6", LocalDate.parse("2026-11-01"),
			LocalDate.parse("2026-11-04"), "CAFE", "abc", 10)).willReturn(new FootprintListResponse(
			List.of(new FootprintListResponse.Item(10L, new BigDecimal("37.5012345"), new BigDecimal("127.0398765"),
				"역삼동", null, null, List.of("CAFE"), Instant.parse("2026-11-04T03:00:00Z"))),
			"next"));

		getAs(USER_ID, "/api/v1/users/me/footprints?bbox=126.9,37.4,127.1,37.6&from=2026-11-01&to=2026-11-04"
			+ "&category=CAFE&cursor=abc&limit=10")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": {
				    "items": [
				      {
				        "id": 10,
				        "latitude": 37.5012345,
				        "longitude": 127.0398765,
				        "locationName": "역삼동",
				        "address": null,
				        "body": null,
				        "categoryCodes": ["CAFE"],
				        "visitedAt": "2026-11-04 12:00:00+09"
				      }
				    ],
				    "nextCursor": "next"
				  }
				}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 쿼리를_모두_생략해도_되고_다음_쪽이_없으면_nextCursor는_null() throws Exception {
		given(footprintService.getMyFootprints(USER_ID, null, null, null, null, null, null))
			.willReturn(new FootprintListResponse(List.of(), null));

		getAs(USER_ID, "/api/v1/users/me/footprints")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{ "success": true, "data": { "items": [], "nextCursor": null } }
				""", JsonCompareMode.STRICT));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		날짜 형식 틀림     | from=2026/11/01
		없는 날짜          | to=2026-02-30
		limit 숫자 아님    | limit=abc
		""")
	void 쿼리_타입이_틀리면_400_C002(String name, String query) throws Exception {
		getAs(USER_ID, "/api/v1/users/me/footprints?" + query)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C002"));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		INVALID_BBOX           | 400 | C013
		INVALID_DATE_RANGE     | 400 | C012
		INVALID_CURSOR         | 400 | C006
		PAGE_SIZE_OUT_OF_RANGE | 400 | C007
		NOT_ALLOWED_VALUE      | 400 | C003
		""")
	void 목록_요청값_오류는_명세의_코드로_응답한다(ExceptionType type, int status, String code) throws Exception {
		given(footprintService.getMyFootprints(USER_ID, null, null, null, null, null, null))
			.willThrow(new BusinessException(type));

		getAs(USER_ID, "/api/v1/users/me/footprints")
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.code").value(code));
	}

	@Test
	void 목록도_인증_정보가_없으면_401_A001() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/footprints"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}

	/* ── API-044 본문 수정 ─────────────────────────────────────── */

	@Test
	void API_044_수정하면_id_body_updatedAt_version으로_응답한다() throws Exception {
		given(footprintService.updateBody(USER_ID, 10L, 1, "바꾼 본문")).willReturn(
			new FootprintUpdateResponse(10L, "바꾼 본문", Instant.parse("2026-11-04T05:30:00Z"), 2));

		patchAs(USER_ID, "/api/v1/footprints/10", """
			{ "version": 1, "body": "바꾼 본문" }
			""")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": { "id": 10, "body": "바꾼 본문", "updatedAt": "2026-11-04 14:30:00+09", "version": 2 }
				}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void 빈_본문이면_body를_null로_내보낸다() throws Exception {
		given(footprintService.updateBody(USER_ID, 10L, 1, "")).willReturn(
			new FootprintUpdateResponse(10L, null, Instant.parse("2026-11-04T05:30:00Z"), 2));

		patchAs(USER_ID, "/api/v1/footprints/10", """
			{ "version": 1, "body": "" }
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.body").value((Object)null))
			.andExpect(jsonPath("$.data.length()").value(4));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		body 생략                 | { "version": 1 }                        | C001
		version 생략              | { "body": "본문" }                      | C001
		빈 객체                   | { }                                     | C001
		body null                 | { "version": 1, "body": null }          | C002
		body 숫자                 | { "version": 1, "body": 123 }           | C002
		body 배열                 | { "version": 1, "body": ["본문"] }      | C002
		version null              | { "version": null, "body": "본문" }     | C002
		version 문자열            | { "version": "1", "body": "본문" }      | C002
		version 소수              | { "version": 1.5, "body": "본문" }      | C002
		version int 범위 밖       | { "version": 3000000000, "body": "본문" } | C002
		JSON 아님                 | version=1                               | C002
		""")
	void 요청값_생략은_C001_null과_타입_오류는_C002(String name, String json, String code) throws Exception {
		patchAs(USER_ID, "/api/v1/footprints/10", json)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
	}

	@Test
	void 본문_201자는_400_C003() throws Exception {
		given(footprintService.updateBody(USER_ID, 10L, 1, "가".repeat(201)))
			.willThrow(new BusinessException(ExceptionType.NOT_ALLOWED_VALUE));

		patchAs(USER_ID, "/api/v1/footprints/10", """
			{ "version": 1, "body": "%s" }
			""".formatted("가".repeat(201)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("C003"));
	}

	@Test
	void version이_다르면_409_C004() throws Exception {
		given(footprintService.updateBody(USER_ID, 10L, 1, "본문"))
			.willThrow(new BusinessException(ExceptionType.VERSION_MISMATCH));

		patchAs(USER_ID, "/api/v1/footprints/10", """
			{ "version": 1, "body": "본문" }
			""")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("C004"));
	}

	@Test
	void 동시_수정으로_낙관적_잠금이_실패해도_409_C004() throws Exception {
		given(footprintService.updateBody(USER_ID, 10L, 1, "본문"))
			.willThrow(new ObjectOptimisticLockingFailureException("Footprint", 10L));

		patchAs(USER_ID, "/api/v1/footprints/10", """
			{ "version": 1, "body": "본문" }
			""")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("C004"));
	}

	@Test
	void 수정도_타인은_403_F002_없으면_404_F001_인증_없으면_401_A001() throws Exception {
		String json = """
			{ "version": 1, "body": "본문" }
			""";
		given(footprintService.updateBody(USER_ID, 10L, 1, "본문"))
			.willThrow(new BusinessException(ExceptionType.FOOTPRINT_NOT_OWNER));
		given(footprintService.updateBody(USER_ID, 11L, 1, "본문"))
			.willThrow(new BusinessException(ExceptionType.FOOTPRINT_NOT_FOUND));

		patchAs(USER_ID, "/api/v1/footprints/10", json)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("F002"));
		patchAs(USER_ID, "/api/v1/footprints/11", json)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("F001"));
		mockMvc.perform(patch("/api/v1/footprints/10").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}

	private ResultActions getAs(Long userId, String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser(userId)));
	}

	private ResultActions patchAs(Long userId, String path, String json) throws Exception {
		return mockMvc.perform(patch(path).with(asUser(userId)).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static RequestPostProcessor asUser(Long userId) {
		return jwt().jwt(token -> token.subject(String.valueOf(userId)));
	}
}
