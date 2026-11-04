package com.ssafy.yeogirok.domain.user.unlink;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ssafy.yeogirok.global.oauth.client.GoogleOAuthClient;
import com.ssafy.yeogirok.global.oauth.client.KakaoUnlinkClient;
import com.ssafy.yeogirok.global.oauth.client.UnlinkProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴 후 제공자 연결 해제(04-auth-api.md §6). 해제 실패가 탈퇴를 막지 않으므로 탈퇴가 커밋된 뒤에 시도하고,
 * 실패하면 {@link ProviderUnlinkTaskStore}(Redis) 에 남겨 주기적으로 다시 시도한다.
 *
 * <p>구글은 탈퇴 요청의 serverAuthCode 로 받은 토큰으로 해제한다. 토큰을 저장하지 않으므로 실패해도 다시 시도할 수 없고,
 * 로그만 남긴다. 재시도 대상({@link ProviderUnlinkTask})은 카카오뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderUnlinkService {

	private final KakaoUnlinkClient kakaoUnlinkClient;
	private final GoogleOAuthClient googleOAuthClient;
	private final ProviderUnlinkTaskStore taskStore;
	private final UnlinkProperties properties;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onUserWithdrawn(UserWithdrawnEvent event) {
		switch (event.provider()) {
			case GOOGLE -> revokeGoogle(event);
			case KAKAO -> unlinkKakaoOrQueue(event);
		}
	}

	private void revokeGoogle(UserWithdrawnEvent event) {
		try {
			googleOAuthClient.revoke(event.googleRevokeToken());
			log.info("Google revoked. userId={}", event.userId());
		} catch (RuntimeException e) {
			log.warn("Google revoke failed. Not retried (token is not stored). userId={}, reason={}", event.userId(),
				e.getMessage());
		}
	}

	/** 재시도 간격·횟수는 application.yaml auth.unlink(1시간 · 24회). 넘긴 작업은 지우지 않고 운영자가 확인한다. */
	@Scheduled(fixedDelayString = "${auth.unlink.retry-interval}", initialDelayString = "${auth.unlink.retry-interval}")
	public void retryFailed() {
		for (ProviderUnlinkTask task : taskStore.findAll()) {
			if (task.attempts() >= properties.maxAttempts()) {
				continue;
			}
			try {
				kakaoUnlinkClient.unlink(task.providerSubject());
				taskStore.delete(task.userId());
				log.info("Provider unlink retried successfully. userId={}, provider={}", task.userId(), task.provider());
			} catch (RuntimeException e) {
				taskStore.recordFailure(task.userId(), e.getMessage());
				log.warn("Provider unlink retry failed. userId={}, provider={}, attempts={}, reason={}",
					task.userId(), task.provider(), task.attempts() + 1, e.getMessage());
			}
		}
	}

	private void unlinkKakaoOrQueue(UserWithdrawnEvent event) {
		try {
			kakaoUnlinkClient.unlink(event.providerSubject());
			log.info("Kakao unlinked. userId={}", event.userId());
		} catch (RuntimeException e) {
			log.warn("Kakao unlink failed. Queued for retry. userId={}, reason={}", event.userId(), e.getMessage());
			queue(event, e.getMessage());
		}
	}

	/** 탈퇴는 이미 커밋됐다. 목록 저장이 실패해도 요청을 실패시키지 않고 로그로 남긴다(운영자가 수동 해제). */
	private void queue(UserWithdrawnEvent event, String error) {
		try {
			taskStore.saveFailed(event, error);
		} catch (RuntimeException e) {
			log.error("Failed to queue provider unlink. Unlink manually. userId={}, provider={}", event.userId(),
				event.provider(), e);
		}
	}
}
