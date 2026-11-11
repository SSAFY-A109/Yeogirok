package com.ssafy.yeogirok.domain.auth.service;

import java.time.Clock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.auth.dto.TokenIssueResponse;
import com.ssafy.yeogirok.domain.auth.dto.TokenRefreshResponse;
import com.ssafy.yeogirok.domain.auth.token.AccessTokenIssuer;
import com.ssafy.yeogirok.domain.auth.token.RefreshTokenService;
import com.ssafy.yeogirok.domain.push.service.PushTokenService;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.global.oauth.oidc.IdTokenVerifier;
import com.ssafy.yeogirok.global.oauth.oidc.OidcIdentity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final IdTokenVerifier idTokenVerifier;
	private final UserRepository userRepository;
	private final AccessTokenIssuer accessTokenIssuer;
	private final RefreshTokenService refreshTokenService;
	private final PushTokenService pushTokenService;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	private record SignIn(
		User user,
		boolean isNewUser
	) {
	}

	/** API-005. 소셜 ID 토큰 검증 → 회원 조회·가입(커밋) → 서비스 토큰 발급. */
	public TokenIssueResponse login(String providerValue, String idToken, String nonce) {
		OAuthProvider provider = OAuthProvider.fromValue(providerValue)
			.orElseThrow(() -> new BusinessException(ExceptionType.UNSUPPORTED_PROVIDER));

		OidcIdentity identity = idTokenVerifier.verify(provider, idToken, nonce);

		SignIn signIn = findOrSignUp(identity);
		User user = signIn.user();
		boolean isNewUser = signIn.isNewUser();

		log.info("Social login. userId={}, provider={}, isNewUser={}", user.getId(), provider, isNewUser);

		return new TokenIssueResponse(
			accessTokenIssuer.issueForLogin(user), accessTokenIssuer.ttlSeconds(),
			refreshTokenService.issueForNewLogin(user.getId()), refreshTokenService.ttlSeconds(),
			isNewUser);
	}

	/** API-006. 탈퇴 직후 세션 폐기가 늦어도 탈퇴 회원에게는 새 토큰을 주지 않는다. */
	@Transactional(readOnly = true)
	public TokenRefreshResponse refresh(String rawRefreshToken) {
		RefreshTokenService.Rotated rotated = refreshTokenService.rotate(rawRefreshToken);
		User user = userRepository.findById(rotated.userId())
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ExceptionType.REFRESH_TOKEN_REVOKED));

		return new TokenRefreshResponse(
			accessTokenIssuer.issueForRefresh(user), accessTokenIssuer.ttlSeconds(),
			rotated.rawToken(), refreshTokenService.ttlSeconds());
	}

	/**
	 * API-007. 이 기기의 리프레시 토큰을 폐기하고, 푸시 토큰을 주면 함께 지운다(그 회원의 그 토큰만, API-096 과 같다).
	 * 푸시 토큰 삭제(DB)가 실패해도 세션은 폐기하고 오류를 돌려준다. 앱이 다시 부르면 둘 다 성공한다(여러 번 불러도 된다).
	 */
	public void logout(Long userId, String rawRefreshToken, String pushToken) {
		try {
			if (pushToken != null) {
				pushTokenService.delete(userId, pushToken);
			}
		} finally {
			refreshTokenService.revoke(userId, rawRefreshToken);
		}
	}

	/**
	 * 처음 로그인하는 계정이 동시에 두 번 오면 한쪽 가입이 (provider, provider_subject) 유일 제약에 걸린다.
	 * 그 트랜잭션은 롤백하고, 새 트랜잭션에서 먼저 가입된 회원을 읽어 로그인시킨다.
	 */
	private SignIn findOrSignUp(OidcIdentity identity) {
		try {
			return transactionTemplate.execute(status -> findOrCreate(identity));
		} catch (DataIntegrityViolationException e) {
			log.info("Concurrent first login. Using the user created by the other request. provider={}",
				identity.provider());
			return transactionTemplate.execute(status -> findOrCreate(identity));
		}
	}

	private SignIn findOrCreate(OidcIdentity identity) {
		return userRepository.findByProviderAndProviderSubject(identity.provider(), identity.subject())
			.map(user -> new SignIn(user, false))
			.orElseGet(() -> new SignIn(signUp(identity), true));
	}

	private User signUp(OidcIdentity identity) {
		return userRepository.saveAndFlush(User.signUp(identity.provider(), identity.subject(), identity.email(),
			identity.nickname(), identity.profileImageUrl(), clock.instant()));
	}
}
