package com.ssafy.yeogirok.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.global.exception.GlobalExceptionHandler;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

/**
 * 시큐리티 필터 없이 standalone MockMvc 로 돌리고, 인증 정보는 SecurityContext 에 직접 넣는다.
 */
class CurrentUserIdArgumentResolverTest {

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void JWT_sub를_사용자_ID로_넣는다() throws Exception {
		authenticate(jwtWithSubject("42"));

		call().andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value(42));
	}

	@Test
	void 인증_정보가_없으면_401_A001() throws Exception {
		call().andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}

	@Test
	void 익명_사용자는_401_A001() throws Exception {
		authenticate(new AnonymousAuthenticationToken("key", "anonymousUser",
			AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		call().andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}

	@Test
	void 사용자_ID가_숫자가_아니면_401_A003() throws Exception {
		authenticate(jwtWithSubject("kakao-123"));

		call().andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A003"));
	}

	@Test
	void 요청_헤더로는_사용자를_바꿀_수_없다() throws Exception {
		mockMvc.perform(get("/test/me").header("X-User-Id", "1").param("userId", "1"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("A001"));
	}

	private ResultActions call() throws Exception {
		return mockMvc.perform(get("/test/me"));
	}

	private static void authenticate(Authentication authentication) {
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private static JwtAuthenticationToken jwtWithSubject(String subject) {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
		return new JwtAuthenticationToken(jwt, List.of());
	}

	@RestController
	static class TestController {

		@GetMapping("/test/me")
		ResponseEntity<ApiResponseBody<Long>> me(@CurrentUserId Long userId) {
			return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(userId));
		}
	}
}
