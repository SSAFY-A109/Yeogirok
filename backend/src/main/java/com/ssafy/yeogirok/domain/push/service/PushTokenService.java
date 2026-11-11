package com.ssafy.yeogirok.domain.push.service;

import java.time.Clock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.push.entity.PushPlatform;
import com.ssafy.yeogirok.domain.push.entity.UserPushToken;
import com.ssafy.yeogirok.domain.push.repository.UserPushTokenRepository;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 푸시 기기 토큰 등록(API-095)·삭제(API-096). 탈퇴 때 전부 지우는 것은 {@code UserService} 가 한다. */
@Service
@RequiredArgsConstructor
public class PushTokenService {

	private final UserPushTokenRepository repository;
	private final UserRepository userRepository;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	/**
	 * API-095. 같은 토큰을 다시 보내도 성공한다. 다른 계정이 등록한 토큰이면 지금 계정으로 옮긴다.
	 *
	 * <p>같은 토큰의 첫 등록이 동시에 오면 한쪽이 기본키 충돌에 걸린다. 그때는 한 번 더 시도해 이미 들어간 행을 갱신한다.
	 */
	public void register(Long userId, String token, PushPlatform platform) {
		try {
			upsert(userId, token, platform);
		} catch (DataIntegrityViolationException e) {
			upsert(userId, token, platform);
		}
	}

	/**
	 * 회원 행을 탈퇴(API-009)와 같은 잠금으로 잡고 활성인지 본다. 탈퇴가 먼저 커밋됐으면 A003 이고, 등록이 먼저면 탈퇴가
	 * 기다렸다가 이 토큰까지 지운다. 탈퇴 직후 아직 유효한 액세스 토큰으로 온 등록이 토큰을 남기지 않는다.
	 */
	private void upsert(Long userId, String token, PushPlatform platform) {
		transactionTemplate.executeWithoutResult(status -> {
			userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ExceptionType.ACCESS_TOKEN_INVALID));
			repository.findById(token).ifPresentOrElse(
				existing -> existing.registerTo(userId, platform, clock.instant()),
				() -> repository.saveAndFlush(new UserPushToken(token, userId, platform, clock.instant())));
		});
	}

	/** API-096. 요청한 회원의 그 토큰만 지운다. 없거나 이미 지웠어도, 다른 계정의 토큰이어도 성공한다(지우지 않는다). */
	public void delete(Long userId, String token) {
		repository.deleteByUserIdAndToken(userId, token);
	}
}
