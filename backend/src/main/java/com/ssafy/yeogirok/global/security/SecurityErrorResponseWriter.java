package com.ssafy.yeogirok.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 필터 단계 실패 응답. 컨트롤러와 같은 {@code ApiResponseUtil} 객체를 스프링의 JSON 매퍼로 쓴다
 * — JSON 을 문자열로 손으로 만들면 필드 이름이 봉투 클래스와 따로 놀게 된다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

	private final JsonMapper jsonMapper;

	public void write(HttpServletResponse response, ExceptionType type) throws IOException {
		response.setStatus(type.getStatus().value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(response.getWriter(), ApiResponseUtil.createFailureResponse(type));
	}
}
