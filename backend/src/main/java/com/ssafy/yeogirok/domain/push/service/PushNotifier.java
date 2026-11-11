package com.ssafy.yeogirok.domain.push.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.push.repository.UserPushTokenRepository;
import com.ssafy.yeogirok.domain.push.sender.PushMessage;
import com.ssafy.yeogirok.domain.push.sender.PushResult;
import com.ssafy.yeogirok.domain.push.sender.PushSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원에게 앱 푸시를 보내는 공용 창구. 채팅·초대·일정 등 기능은 받을 회원과 알림 모양만 정하고 여기로 보낸다.
 *
 * <p>하는 일: 그 회원의 등록 기기 토큰을 읽어 모두에 보내고, FCM 이 UNREGISTERED 를 준 토큰은 <b>보낼 당시 주인과 같을 때만</b>
 * 지운다(그사이 다른 계정으로 옮겨 간 토큰은 둔다). 받는 사람 고르기·발송 제한·비동기 실행은 부르는 기능이 정한다.
 * 이 메서드는 FCM 응답까지 기다리므로 요청 스레드가 아니라 기능의 발송 스레드에서 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotifier {

	private final UserPushTokenRepository tokenRepository;
	private final PushSender sender;

	/** 실제로 보낼 수 있는지(FCM 키가 있는지). false 면 부르는 쪽이 받는 사람 조회부터 건너뛰면 된다. */
	public boolean isEnabled() {
		return sender.isEnabled();
	}

	/** 그 회원의 등록 기기 모두에 보낸다. 예외를 던지지 않는다(DB 오류는 부르는 쪽에서 잡는다). */
	public PushOutcome sendToUser(Long userId, PushMessage message) {
		List<String> tokens = tokenRepository.findTokensByUserId(userId);
		if (tokens.isEmpty()) {
			return PushOutcome.NO_DEVICE;
		}
		List<PushResult> results = sender.send(tokens, message);
		for (int i = 0; i < tokens.size() && i < results.size(); i++) {
			if (results.get(i) == PushResult.UNREGISTERED) {
				removeUnregistered(userId, tokens.get(i));
			}
		}
		return outcomeOf(results);
	}

	private void removeUnregistered(Long userId, String token) {
		if (tokenRepository.deleteByUserIdAndToken(userId, token) > 0) {
			log.info("FCM 이 등록 해제된 토큰이라 알려 지웠습니다. userId={}", userId);
		} else {
			log.info("FCM 이 등록 해제된 토큰이라 알렸지만, 그사이 다른 계정으로 옮겨 갔거나 지워져 두었습니다. userId={}", userId);
		}
	}

	static PushOutcome outcomeOf(List<PushResult> results) {
		if (results.contains(PushResult.SENT)) {
			return PushOutcome.SENT;
		}
		if (results.stream().allMatch(result -> result == PushResult.FAILED || result == PushResult.UNREGISTERED)) {
			return PushOutcome.REJECTED;
		}
		return PushOutcome.UNAVAILABLE;
	}
}
