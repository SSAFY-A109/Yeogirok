package com.ssafy.yeogirok.domain.course.seed;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공식 코스 주인(명세 6-5). 설정 회원이 있으면 그 회원(없거나 탈퇴면 empty), 없으면 로그인할 수 없는 시스템 계정.
 * users 에 (provider, provider_subject) 유니크 제약이 없어, 적재 잠금 안에서만 불러 시스템 계정이 두 개 생기지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SeedOwner {

	/** 카카오 로그인은 숫자 회원번호(sub)로 찾으므로 이 값으로는 로그인할 수 없다. */
	static final String SYSTEM_SUBJECT = "system:tourapi";
	static final String SYSTEM_NICKNAME = "한국관광공사 여행코스";

	private final UserRepository userRepository;
	private final Clock clock;

	Optional<Long> resolve(Long configuredUserId) {
		if (configuredUserId != null) {
			Optional<Long> owner = userRepository.findById(configuredUserId).filter(User::isActive).map(User::getId);
			if (owner.isEmpty()) {
				log.warn("공식 코스 주인 회원이 없거나 탈퇴 상태라 적재하지 않습니다. userId={}", configuredUserId);
			}
			return owner;
		}
		return Optional.of(userRepository.findByProviderAndProviderSubject(OAuthProvider.KAKAO, SYSTEM_SUBJECT)
			.orElseGet(() -> userRepository.save(
				User.signUp(OAuthProvider.KAKAO, SYSTEM_SUBJECT, null, SYSTEM_NICKNAME, null, clock.instant())))
			.getId());
	}
}
