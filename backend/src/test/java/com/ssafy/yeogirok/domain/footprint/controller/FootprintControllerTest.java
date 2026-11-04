package com.ssafy.yeogirok.domain.footprint.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDeleteResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintListResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateResponse;
import com.ssafy.yeogirok.domain.footprint.service.FootprintCreateService;
import com.ssafy.yeogirok.domain.footprint.service.FootprintService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
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
	static final String UPDATE_JSON = """
		{ "version": 1, "body": "본문" }
		""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	FootprintService footprintService;

	/** API-041 은 FootprintCreateControllerTest 가 본다. 컨트롤러를 띄우려고만 둔다. */
	@MockitoBean
	FootprintCreateService footprintCreateService;

	@MockitoBean
	IdempotencyService idempotencyService;

	@Test
	void API_043_상세는_명세의_필드와_datetime_형식으로_응답하고_없는_값은_null로_내보낸다() throws Exception {
		given(footprintService.getFootprint(USER_ID, 10L)).willReturn(new FootprintDetailResponse(
			10L, new BigDecimal("37.5012345"), new BigDecimal("127.0398765"), "역삼동", null,
			"좋았다\n또 올게", List.of("CAFE", "FOOD"),
			Instant.parse("2026-11-04T03:00:00Z"), Instant.parse("2026-11-04T03:00:05Z"), 1));

		getAs(USER_ID, "/api/v1/footprints/10")
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{
				  "success": true,
				  "data": {
				    "id": 10, "latitude": 37.5012345, "longitude": 127.0398765,
				    "locationName": "역삼동", "address": null, "body": "좋았다\\n또 올게",
				    "categoryCodes": ["CAFE", "FOOD"],
				    "visitedAt": "2026-11-04 12:00:00+09", "createdAt": "2026-11-04 12:00:05+09",
				    "version": 1
				  }
				}
				""", JsonCompareMode.STRICT));
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
				        "id": 10, "latitude": 37.5012345, "longitude": 127.0398765,
				        "locationName": "역삼동", "address": null, "body": null,
				        "categoryCodes": ["CAFE"], "visitedAt": "2026-11-04 12:00:00+09"
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
	void 동시_수정으로_낙관적_잠금이_실패해도_409_C004() throws Exception {
		given(footprintService.updateBody(USER_ID, 10L, 1, "본문"))
			.willThrow(new ObjectOptimisticLockingFailureException("Footprint", 10L));

		patchAs(USER_ID, "/api/v1/footprints/10", UPDATE_JSON)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("C004"));
	}

	/* ── API-045 삭제 ─────────────────────────────────────────── */

	@Test
	void API_045_삭제하면_deleted와_statisticsStatus로_응답한다() throws Exception {
		given(footprintService.delete(USER_ID, 10L, 3)).willReturn(FootprintDeleteResponse.applied());

		mockMvc.perform(delete("/api/v1/footprints/10").param("version", "3").with(asUser(USER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{ "success": true, "data": { "deleted": true, "statisticsStatus": "APPLIED" } }
				""", JsonCompareMode.STRICT));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', textBlock = """
		삭제 version 생략 | DELETE | /api/v1/footprints/10             | C001
		삭제 version 문자 | DELETE | /api/v1/footprints/10?version=a   | C002
		삭제 version 소수 | DELETE | /api/v1/footprints/10?version=1.5 | C002
		삭제 id 문자      | DELETE | /api/v1/footprints/abc?version=1  | C002
		상세 id 문자      | GET    | /api/v1/footprints/abc            | C002
		""")
	void 경로나_쿼리_값이_틀리면_400(String name, String method, String path, String code) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), path).with(asUser(USER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code));
	}

	/* ── 공통: 서비스 오류 매핑·인증 ───────────────────────────── */

	@ParameterizedTest(name = "{0} {1}")
	@CsvSource(delimiter = '|', textBlock = """
		GET    | FOOTPRINT_NOT_OWNER | 403 | F002
		GET    | FOOTPRINT_NOT_FOUND | 404 | F001
		PATCH  | FOOTPRINT_NOT_OWNER | 403 | F002
		PATCH  | FOOTPRINT_NOT_FOUND | 404 | F001
		PATCH  | VERSION_MISMATCH    | 409 | C004
		PATCH  | NOT_ALLOWED_VALUE   | 400 | C003
		DELETE | FOOTPRINT_NOT_OWNER | 403 | F002
		DELETE | FOOTPRINT_NOT_FOUND | 404 | F001
		DELETE | VERSION_MISMATCH    | 409 | C004
		LIST   | INVALID_BBOX           | 400 | C013
		LIST   | INVALID_DATE_RANGE     | 400 | C012
		LIST   | INVALID_CURSOR         | 400 | C006
		LIST   | PAGE_SIZE_OUT_OF_RANGE | 400 | C007
		LIST   | NOT_ALLOWED_VALUE      | 400 | C003
		""")
	void 서비스_오류는_명세의_코드로_응답한다_명세의_코드로_응답한다(String method, ExceptionType type, int status, String code)
		throws Exception {
		BusinessException e = new BusinessException(type);
		ResultActions result = switch (method) {
			case "GET" -> {
				given(footprintService.getFootprint(USER_ID, 10L)).willThrow(e);
				yield getAs(USER_ID, "/api/v1/footprints/10");
			}
			case "PATCH" -> {
				given(footprintService.updateBody(USER_ID, 10L, 1, "본문")).willThrow(e);
				yield patchAs(USER_ID, "/api/v1/footprints/10", UPDATE_JSON);
			}
			case "LIST" -> {
				given(footprintService.getMyFootprints(USER_ID, null, null, null, null, null, null)).willThrow(e);
				yield getAs(USER_ID, "/api/v1/users/me/footprints");
			}
			default -> {
				given(footprintService.delete(USER_ID, 10L, 1)).willThrow(e);
				yield mockMvc.perform(delete("/api/v1/footprints/10").param("version", "1").with(asUser(USER_ID)));
			}
		};
		result.andExpect(status().is(status))
			.andExpect(jsonPath("$.code").value(code));
	}

	@ParameterizedTest(name = "{0} {1}")
	@CsvSource(delimiter = '|', textBlock = """
		GET    | /api/v1/footprints/10
		GET    | /api/v1/users/me/footprints
		PATCH  | /api/v1/footprints/10
		DELETE | /api/v1/footprints/10?version=1
		""")
	void 인증_정보가_없으면_401_A001(String method, String path) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), path)
				.contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
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
