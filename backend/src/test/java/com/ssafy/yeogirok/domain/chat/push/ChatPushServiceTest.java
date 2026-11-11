package com.ssafy.yeogirok.domain.chat.push;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.chat.dto.ChatMessage;
import com.ssafy.yeogirok.domain.push.repository.UserPushTokenRepository;
import com.ssafy.yeogirok.domain.push.sender.NoOpPushSender;

/** FCM 키가 없으면(보내지 않는 발송기) 수신자 조회·Redis 작업을 하지 않는다. */
class ChatPushServiceTest {

	@Test
	void 보낼_수_없는_발송기면_아무것도_조회하지_않는다() {
		UserPushTokenRepository repository = mock(UserPushTokenRepository.class);
		ChatPresence presence = mock(ChatPresence.class);
		ChatPushThrottle throttle = mock(ChatPushThrottle.class);
		ChatPushService service = new ChatPushService(repository, presence, throttle, new NoOpPushSender(),
			new ChatPushProperties(Duration.ofSeconds(90), Duration.ofSeconds(3), 40, 1, 10));
		try {
			service.notifyNewMessage(1L,
				new ChatMessage(10L, new ChatMessage.Sender(2L, "보경"), "안녕", Instant.EPOCH, "client-1"));
		} finally {
			service.shutdown();
		}

		verifyNoInteractions(repository, presence, throttle);
	}
}
