package com.ssafy.yeogirok.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.push.repository.UserDeviceToken;
import com.ssafy.yeogirok.domain.push.repository.UserPushTokenRepository;
import com.ssafy.yeogirok.domain.push.sender.PushMessage;
import com.ssafy.yeogirok.domain.push.sender.PushResult;
import com.ssafy.yeogirok.domain.push.sender.PushSender;

/** 공용 발송 창구: 여러 회원의 기기에 한 번에 보내고, 해제된 토큰은 주인이 같을 때만 지우고, 결과를 회원마다 묶는다. */
class PushNotifierTest {

	private static final PushMessage MESSAGE = new PushMessage("제목", "본문", Map.of("type", "TEST"), null, null);

	private final UserPushTokenRepository tokens = mock(UserPushTokenRepository.class);
	private final PushSender sender = mock(PushSender.class);
	private final PushNotifier notifier = new PushNotifier(tokens, sender);

	@Test
	void 기기가_없으면_보내지_않는다() {
		when(tokens.findTokensByUserIds(Set.of(1L))).thenReturn(List.of());

		assertThat(notifier.sendToUser(1L, MESSAGE)).isEqualTo(PushOutcome.NO_DEVICE);
		verify(sender, never()).send(any(), any());
	}

	@Test
	void 여러_회원의_기기를_한_번에_보내고_결과는_회원마다_묶는다() {
		when(tokens.findTokensByUserIds(Set.of(1L, 2L, 3L))).thenReturn(List.of(
			new UserDeviceToken(1L, "phone"), new UserDeviceToken(1L, "tablet"), new UserDeviceToken(2L, "old")));
		when(sender.send(List.of("phone", "tablet", "old"), MESSAGE))
			.thenReturn(List.of(PushResult.SENT, PushResult.UNREGISTERED, PushResult.UNREGISTERED));

		Map<Long, PushOutcome> outcomes = notifier.sendToUsers(List.of(1L, 2L, 3L), MESSAGE);

		assertThat(outcomes).containsExactly(Map.entry(1L, PushOutcome.SENT), Map.entry(2L, PushOutcome.REJECTED),
			Map.entry(3L, PushOutcome.NO_DEVICE));
		verify(sender, times(1)).send(any(), any());
		// 해제된 토큰은 그 회원 것으로만 지운다.
		verify(tokens).deleteByUserIdAndToken(1L, "tablet");
		verify(tokens).deleteByUserIdAndToken(2L, "old");
		verify(tokens, never()).deleteByUserIdAndToken(1L, "phone");
	}

	@Test
	void 받을_회원이_없으면_아무것도_하지_않는다() {
		assertThat(notifier.sendToUsers(List.of(), MESSAGE)).isEmpty();
		verify(tokens, never()).findTokensByUserIds(any());
	}

	@Test
	void 결과를_토큰_거절과_FCM_장애로_나눈다() {
		assertThat(PushNotifier.outcomeOf(List.of())).isEqualTo(PushOutcome.NO_DEVICE);
		assertThat(PushNotifier.outcomeOf(List.of(PushResult.FAILED, PushResult.UNREGISTERED)))
			.isEqualTo(PushOutcome.REJECTED);
		assertThat(PushNotifier.outcomeOf(List.of(PushResult.FAILED, PushResult.UNAVAILABLE)))
			.isEqualTo(PushOutcome.UNAVAILABLE);
		assertThat(PushNotifier.outcomeOf(List.of(PushResult.UNAVAILABLE, PushResult.SENT)))
			.isEqualTo(PushOutcome.SENT);
	}

	@Test
	void 보낼_수_있는지는_발송기를_따른다() {
		when(sender.isEnabled()).thenReturn(false);

		assertThat(notifier.isEnabled()).isFalse();
	}
}
