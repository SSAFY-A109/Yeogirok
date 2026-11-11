package com.ssafy.yeogirok.domain.push.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.push.repository.UserDeviceToken;
import com.ssafy.yeogirok.domain.push.repository.UserPushTokenRepository;
import com.ssafy.yeogirok.domain.push.sender.PushMessage;
import com.ssafy.yeogirok.domain.push.sender.PushResult;
import com.ssafy.yeogirok.domain.push.sender.PushSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원에게 앱 푸시를 보내는 공용 창구. 채팅·초대·일정 등 기능은 받을 회원과 알림 모양만 정하고 여기로 보낸다.
 *
 * <p>하는 일: 받을 회원들의 등록 기기 토큰을 한 번에 읽어 FCM 에 한 번에 보내고(500개씩 나눠 sendEach), FCM 이 UNREGISTERED 를
 * 준 토큰은 <b>보낼 당시 주인과 같을 때만</b> 지운다(그사이 다른 계정으로 옮겨 간 토큰은 둔다). 받는 사람 고르기·발송 제한·비동기
 * 실행은 부르는 기능이 정한다. FCM 응답까지 기다리므로 요청 스레드가 아니라 기능의 발송 스레드에서 부른다.
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

	/** 한 명에게 보낸다. {@link #sendToUsers} 와 같다. */
	public PushOutcome sendToUser(Long userId, PushMessage message) {
		return sendToUsers(List.of(userId), message).get(userId);
	}

	/**
	 * 같은 알림을 여러 회원의 등록 기기 모두에 보낸다. 토큰 조회 한 번, FCM 호출은 토큰 500개마다 한 번이다.
	 * 결과는 회원마다 하나({@code userIds} 순서, 중복은 하나로). 예외를 던지지 않는다(DB 오류는 부르는 쪽에서 잡는다).
	 */
	public Map<Long, PushOutcome> sendToUsers(Collection<Long> userIds, PushMessage message) {
		Map<Long, List<PushResult>> resultsByUser = new LinkedHashMap<>();
		userIds.forEach(userId -> resultsByUser.put(userId, new ArrayList<>()));
		if (resultsByUser.isEmpty()) {
			return Map.of();
		}
		List<UserDeviceToken> devices = tokenRepository.findTokensByUserIds(resultsByUser.keySet());
		if (!devices.isEmpty()) {
			List<PushResult> results = sender.send(devices.stream().map(UserDeviceToken::token).toList(), message);
			for (int i = 0; i < devices.size() && i < results.size(); i++) {
				UserDeviceToken device = devices.get(i);
				resultsByUser.get(device.userId()).add(results.get(i));
				if (results.get(i) == PushResult.UNREGISTERED) {
					removeUnregistered(device);
				}
			}
		}
		Map<Long, PushOutcome> outcomes = new LinkedHashMap<>();
		resultsByUser.forEach((userId, results) -> outcomes.put(userId, outcomeOf(results)));
		return outcomes;
	}

	private void removeUnregistered(UserDeviceToken device) {
		if (tokenRepository.deleteByUserIdAndToken(device.userId(), device.token()) > 0) {
			log.info("FCM 이 등록 해제된 토큰이라 알려 지웠습니다. userId={}", device.userId());
		} else {
			log.info("FCM 이 등록 해제된 토큰이라 알렸지만, 그사이 다른 계정으로 옮겨 갔거나 지워져 두었습니다. userId={}",
				device.userId());
		}
	}

	static PushOutcome outcomeOf(List<PushResult> results) {
		if (results.isEmpty()) {
			return PushOutcome.NO_DEVICE;
		}
		if (results.contains(PushResult.SENT)) {
			return PushOutcome.SENT;
		}
		if (results.stream().allMatch(result -> result == PushResult.FAILED || result == PushResult.UNREGISTERED)) {
			return PushOutcome.REJECTED;
		}
		return PushOutcome.UNAVAILABLE;
	}
}
