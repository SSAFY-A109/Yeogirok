package com.ssafy.yeogirok.global.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.auth.token.AccessTokenExpiryValidator;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 401 을 명세의 세 갈래로 나눈다. 앱은 A002 일 때만 토큰을 갱신하고 다시 시도한다.
 *
 * <ul>
 *   <li>A001 — 토큰이 아예 없음(Bearer 헤더 없음). 익명으로 보호 경로에 들어와 여기로 온다
 *   <li>A002 — 서명·클레임은 맞는데 만료됨({@link AccessTokenExpiryValidator} 가 남긴 오류 코드)
 *   <li>A003 — 그 밖의 토큰 오류(서명·형식·발급자·클레임)
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityErrorResponseWriter writer;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
		throws IOException {
		writer.write(response, classify(exception));
	}

	private ExceptionType classify(AuthenticationException exception) {
		if (!(exception instanceof InvalidBearerTokenException)) {
			return ExceptionType.ACCESS_TOKEN_MISSING;
		}
		return isExpired(exception) ? ExceptionType.ACCESS_TOKEN_EXPIRED : ExceptionType.ACCESS_TOKEN_INVALID;
	}

	private boolean isExpired(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof JwtValidationException validation) {
				return validation.getErrors().stream()
					.anyMatch(error -> AccessTokenExpiryValidator.EXPIRED_ERROR_CODE.equals(error.getErrorCode()));
			}
		}
		return false;
	}
}
