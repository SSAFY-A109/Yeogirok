package com.ssafy.yeogirok.domain.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.ssafy.yeogirok.domain.auth.token.RefreshTokenService;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.dto.MyAccountResponse;
import com.ssafy.yeogirok.domain.user.dto.WithdrawResponse;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.global.oauth.client.GoogleOAuthClient;
import com.ssafy.yeogirok.global.oauth.client.GoogleOAuthClient.GoogleTokens;
import com.ssafy.yeogirok.global.oauth.oidc.IdTokenVerifier;
import com.ssafy.yeogirok.global.oauth.oidc.OidcIdentity;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final RefreshTokenService refreshTokenService;
	private final GoogleOAuthClient googleOAuthClient;
	private final IdTokenVerifier idTokenVerifier;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;
	private final WithdrawalProperties properties;
	private final Clock clock;

	/** API-008. */
	@Transactional(readOnly = true)
	public MyAccountResponse getMyAccount(Long userId) {
		User user = findActiveUser(userId);

		// TODO: 발자취·팁 도메인이 생기면 activityCounts 를 실제 개수로 바꾼다(지금은 0 고정).
		return new MyAccountResponse(
			user.getId(),
			user.getNickname(),
			user.getProfileImageUrl(),
			new MyAccountResponse.ActivityCounts(0, 0),
			List.of(user.getProvider().getValue()));
	}

	/**
	 * API-009. 순서: 최근 재인증 확인(A011) → 구글이면 serverAuthCode 교환(외부 호출이라 트랜잭션 밖) →
	 * 계정 비활성화 → 커밋 뒤 모든 기기 세션 폐기(Redis)와 제공자 연결 해제
	 * ({@link com.ssafy.yeogirok.domain.user.unlink.ProviderUnlinkService}). 세션 폐기·연결 해제가 실패해도 탈퇴는 성공한다.
	 *
	 * <p>발급된 액세스 토큰은 만료(최대 TTL)까지 서명이 유효하다. 회원을 조회하는 API 는
	 * {@link #findActiveUser} 로 탈퇴 회원을 막는다.
	 */
	public WithdrawResponse withdraw(AuthenticatedUser principal, String googleAuthCode) {
		User user = findActiveUser(principal.userId());
		requireRecentLogin(principal.authenticatedAt());
		String googleRevokeToken = user.getProvider() == OAuthProvider.GOOGLE
			? exchangeGoogleAuthCode(user, googleAuthCode).revocableToken()
			: null;

		UserWithdrawnEvent event = new UserWithdrawnEvent(user.getId(), user.getProvider(), user.getProviderSubject(),
			googleRevokeToken);
		transactionTemplate.executeWithoutResult(status -> {
			// 회원 행을 잠근다. 동시에 온 두 번째 탈퇴는 기다렸다가 탈퇴 상태를 보고 A003 으로 끝난다(이벤트가 두 번 나가지 않는다).
			User target = userRepository.findByIdForUpdate(principal.userId())
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ExceptionType.ACCESS_TOKEN_INVALID));
			target.withdraw(clock.instant());
			// TODO: 탈퇴 데이터 정리(노션 ERD › ERD 설계 관련 사항). 발자취 PR(S15P31A109-35)이 합쳐진 뒤 구현한다.
			//  - 위에서 잠근 회원 행 아래에서(발자취 UserLock 도 같은 잠금으로 맞춘다) 미삭제 발자취를 footprint_statistics 에 합산하고,
			//    그 회원의 발자취 원본(삭제 상태 포함)을 지운다. 계정 비활성화와 같은 이 트랜잭션에서 한다.
			//    cell_id 는 기본 셀 "{floor(lat/기본셀)}:{floor(lng/기본셀)}", stat_date 는 방문 시각의 Asia/Seoul 날짜.
			//  - 이야기·사진·채팅·AR 팁의 작성자 표시 익명화, 참여자 명단에서 빼기(남은 참여자가 0명이면 방 삭제).
			//  - 응답의 dataCleanupStatus=QUEUED 가 실제 정리 작업과 이어지게 한다.
			eventPublisher.publishEvent(event);
		});

		log.info("User withdrawn. userId={}, provider={}, revokedSessions={}", user.getId(), user.getProvider(),
			revokeAllSessions(user.getId()));
		return WithdrawResponse.queued();
	}

	/** Redis 는 DB 트랜잭션에 묶이지 않아 커밋 뒤에 폐기한다. 실패해도 재발급(API-006)이 탈퇴 회원을 막는다. */
	private long revokeAllSessions(Long userId) {
		try {
			return refreshTokenService.revokeAllSessions(userId);
		} catch (RuntimeException e) {
			log.error("Failed to revoke sessions of withdrawn user. userId={}", userId, e);
			return 0;
		}
	}

	/** 재발급(API-006) 토큰에는 로그인 시각이 없어 재인증으로 보지 않는다. 앱은 다시 로그인(API-005)한 뒤 탈퇴한다. */
	private void requireRecentLogin(Instant authenticatedAt) {
		if (authenticatedAt == null || authenticatedAt.plus(properties.reauthMaxAge()).isBefore(clock.instant())) {
			throw new BusinessException(ExceptionType.REAUTHENTICATION_REQUIRED);
		}
	}

	/** 코드로 받은 ID 토큰의 sub 가 이 회원과 같아야 한다(다른 구글 계정의 코드로 해제하지 않는다). */
	private GoogleTokens exchangeGoogleAuthCode(User user, String googleAuthCode) {
		if (!StringUtils.hasText(googleAuthCode)) {
			throw new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING, "구글 회원은 googleAuthCode 가 필요합니다.");
		}

		GoogleTokens tokens;
		try {
			tokens = googleOAuthClient.exchange(googleAuthCode);
		} catch (RuntimeException e) {
			log.info("Google auth code exchange failed. userId={}, reason={}", user.getId(), e.getMessage());
			throw new BusinessException(ExceptionType.GOOGLE_AUTH_CODE_INVALID);
		}

		OidcIdentity identity;
		try {
			identity = idTokenVerifier.verifyFromTokenEndpoint(OAuthProvider.GOOGLE, tokens.idToken());
		} catch (BusinessException e) {
			throw new BusinessException(ExceptionType.GOOGLE_AUTH_CODE_INVALID);
		}
		if (!identity.subject().equals(user.getProviderSubject())) {
			log.info("Google auth code belongs to another account. userId={}", user.getId());
			throw new BusinessException(ExceptionType.GOOGLE_AUTH_CODE_INVALID);
		}
		return tokens;
	}

	/** 토큰의 회원이 없거나 탈퇴했으면 토큰이 유효하지 않은 것으로 본다(명세의 인증 실패는 A001~A003 뿐이다). */
	private User findActiveUser(Long userId) {
		return userRepository.findById(userId)
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ExceptionType.ACCESS_TOKEN_INVALID));
	}
}
