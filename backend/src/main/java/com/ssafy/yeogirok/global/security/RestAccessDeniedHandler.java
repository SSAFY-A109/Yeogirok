package com.ssafy.yeogirok.global.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.ExceptionType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 인증은 됐지만 ROLE 이 부족한 경우(A004). */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final SecurityErrorResponseWriter writer;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
		throws IOException {
		writer.write(response, ExceptionType.ROLE_INSUFFICIENT);
	}
}
