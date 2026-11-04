package com.ssafy.yeogirok.global.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 현재 사용자 ID 를 꺼내는 <b>유일한 곳</b>. {@link Authentication#getName()} 을 사용자 ID 로 읽고, 인증 없으면 A001.
 * TODO: 실제 토큰 클레임은 로그인 담당과 맞춘 뒤 이 클래스만 고친다.
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUserId.class)
			&& Long.class.equals(parameter.getParameterType());
	}

	@Override
	public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken
			|| !authentication.isAuthenticated()) {
			throw new BusinessException(ExceptionType.ACCESS_TOKEN_MISSING);
		}
		try {
			return Long.valueOf(authentication.getName());
		} catch (NumberFormatException e) {
			throw new BusinessException(ExceptionType.ACCESS_TOKEN_INVALID);
		}
	}
}
