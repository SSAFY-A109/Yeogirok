package com.ssafy.yeogirok.global.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** 서비스 액세스 토큰 → {@code AuthenticatedUser} + {@code ROLE_<role>}. */
@Component
public class ServiceJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"));
		return UsernamePasswordAuthenticationToken.authenticated(principal(jwt.getSubject()), jwt, List.of(authority));
	}

	private AuthenticatedUser principal(String subject) {
		try {
			long userId = Long.parseLong(subject);
			if (userId <= 0) {
				throw new NumberFormatException();
			}
			return new AuthenticatedUser(userId);
		} catch (NumberFormatException e) {
			throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN));
		}
	}
}
