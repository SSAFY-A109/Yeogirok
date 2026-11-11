package com.ssafy.yeogirok.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * 채팅 WebSocket(API-077) 연결 경로. 인증은 HTTP 가 아니라 연결 뒤 첫 AUTH 프레임의 1회용 티켓으로 한다.
	 * 아래 기본 체인과 분리해 Bearer 토큰 검사를 아예 걸지 않는다. 같은 체인에서 permitAll 만 하면 앱이 습관처럼 붙인
	 * 만료 토큰 헤더 때문에 핸드셰이크가 401 로 실패한다.
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain chatWebSocketFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/ws/**")
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
		return http.build();
	}

	/**
	 * 로그인 없이 브라우저·안드로이드가 여는 웹 경로. App Links 확인 파일과 초대 링크 안내 페이지(domain/applink).
	 * 안드로이드는 확인 파일이 401 이면 링크를 앱과 묶지 않는다. 채팅과 같은 이유로 Bearer 검사를 아예 걸지 않는다.
	 * GET 만 연다. 다른 메서드는 이 체인에서 거부한다.
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain publicWebFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/.well-known/assetlinks.json", "/invite/**")
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.GET, "/.well-known/assetlinks.json", "/invite/*").permitAll()
				.anyRequest().denyAll());
		return http.build();
	}

	@Bean
	@Order(3)
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder serviceJwtDecoder,
		ServiceJwtAuthenticationConverter authenticationConverter,
		RestAuthenticationEntryPoint authenticationEntryPoint,
		RestAccessDeniedHandler accessDeniedHandler) throws Exception {

		http
			// 모바일 앱 + Bearer 토큰이라 쿠키 기반 공격(CSRF)이 없고, 세션도 쓰지 않는다.
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/token", "/api/v1/auth/token/refresh").permitAll()
				.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
				.requestMatchers("/error").permitAll()
				.anyRequest().hasRole("USER"))

			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.decoder(serviceJwtDecoder).jwtAuthenticationConverter(authenticationConverter))
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))

			.exceptionHandling(exceptionHandling -> exceptionHandling
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));

		return http.build();
	}
}
