package com.ssafy.yeogirok.domain.footprint.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
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

	private ResultActions getAs(Long userId, String path) throws Exception {
		return mockMvc.perform(get(path).with(jwt().jwt(token -> token.subject(String.valueOf(userId)))));
	}
}
