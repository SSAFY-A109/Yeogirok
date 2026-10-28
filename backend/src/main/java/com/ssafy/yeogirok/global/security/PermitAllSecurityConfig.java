package com.ssafy.yeogirok.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 임시 보안 설정. 회원(인증) 기능이 들어오기 전까지 <b>모든 요청을 인증 없이 허용</b>한다.
 *
 * <p>이 설정이 없으면 스프링 시큐리티 기본값이 모든 경로(Swagger 포함)를 막고 로그인 화면을 띄운다.
 *
 * <p><b>TODO: 회원 기능 완료 후 반드시 이 파일을 삭제한다.</b> 실제 SecurityConfig 와 함께 두면
 * 모든 요청에 걸리는 필터 체인이 둘이 되어 앱이 뜨지 않고, 남겨 두면 모든 API 가 인증 없이 열린다.
 */
@Configuration
public class PermitAllSecurityConfig {

	@Bean
	public SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			// 모바일 앱 + Bearer 토큰이라 쿠키 기반 공격(CSRF)이 없고, 세션도 쓰지 않는다.
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

		return http.build();
	}
}
