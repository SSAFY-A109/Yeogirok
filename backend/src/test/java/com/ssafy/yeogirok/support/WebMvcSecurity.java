package com.ssafy.yeogirok.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ssafy.yeogirok.global.security.AuthenticatedUser;
import com.ssafy.yeogirok.global.security.RestAccessDeniedHandler;
import com.ssafy.yeogirok.global.security.RestAuthenticationEntryPoint;
import com.ssafy.yeogirok.global.security.SecurityConfig;
import com.ssafy.yeogirok.global.security.SecurityErrorResponseWriter;
import com.ssafy.yeogirok.global.security.ServiceJwtAuthenticationConverter;

/** @WebMvcTest 에 실제 보안 설정을 붙인다. 요청 사용자는 {@link #asUser} 로 넣으므로 토큰 디코더는 쓰이지 않는다. */
@TestConfiguration
@Import({SecurityConfig.class, ServiceJwtAuthenticationConverter.class, RestAuthenticationEntryPoint.class,
	RestAccessDeniedHandler.class, SecurityErrorResponseWriter.class})
public class WebMvcSecurity {

	@Bean
	JwtDecoder serviceJwtDecoder() {
		return token -> {
			throw new BadJwtException("테스트에서는 토큰을 디코딩하지 않는다");
		};
	}

	/** 서비스 토큰을 검증한 뒤와 같은 인증(AuthenticatedUser + ROLE_USER). */
	public static RequestPostProcessor asUser(Long userId) {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(
			new AuthenticatedUser(userId, Instant.now()), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
	}
}
