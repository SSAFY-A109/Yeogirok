package com.ssafy.yeogirok.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.push.repository.UserPushTokenRepository;
import com.ssafy.yeogirok.domain.push.sender.PushMessage;
import com.ssafy.yeogirok.domain.push.sender.PushResult;
import com.ssafy.yeogirok.domain.push.sender.PushSender;

/** 공용 발송 창구: 회원의 기기 모두에 보내고, 해제된 토큰은 주인이 같을 때만 지우고, 결과를 하나로 묶는다. */
class PushNotifierTest {

	private static final PushMessage MESSAGE = new PushMessage("제목", "본문", Map.of("type", "TEST"), null, null);

	private final UserPushTokenRepository tokens = mock(UserPushTokenRepository.class);
	private final PushSender sender = mock(PushSender.class);
	private final PushNotifier notifier = new PushNotifier(tokens, sender);

	@Test
	void 기기가_없으면_보내지_않는다() {
		when(tokens.findTokensByUserId(1L)).thenReturn(List.of());

		assertThat(notifier.sendToUser(1L, MESSAGE)).isEqualTo(PushOutcome.NO_DEVICE);
		verify(sender, never()).send(any(), any());
	}

	@Test
	void 등록_기기_모두에_보내고_해제된_토큰은_그_회원_것만_지운다() {
		when(tokens.findTokensByUserId(1L)).thenReturn(List.of("phone", "tablet"));
		when(sender.send(List.of("phone", "tablet"), MESSAGE))
			.thenReturn(List.of(PushResult.SENT, PushResult.UNREGISTERED));

		assertThat(notifier.sendToUser(1L, MESSAGE)).isEqualTo(PushOutcome.SENT);
		verify(tokens).deleteByUserIdAndToken(1L, "tablet");
		verify(tokens, never()).deleteByUserIdAndToken(1L, "phone");
	}

	@Test
	void 결과를_토큰_거절과_FCM_장애로_나눈다() {
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
