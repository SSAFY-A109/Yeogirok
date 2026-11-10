package com.ssafy.yeogirok.domain.chat.realtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.web.socket.WebSocketSession;

import com.ssafy.yeogirok.domain.chat.ChatProperties;

import tools.jackson.databind.json.JsonMapper;

/** 방 구독. 구독이 실패해도 빈 방이 맵에 남지 않아 다음 연결이 다시 구독한다. */
class ChatHubTest {

	static final ChannelTopic ROOM_TOPIC = new ChannelTopic("chat:room:10");

	RedisMessageListenerContainer container;
	ChatHub hub;

	@BeforeEach
	void setUp() {
		container = mock(RedisMessageListenerContainer.class);
		ChatProperties properties = new ChatProperties(Duration.ofSeconds(30), Duration.ofSeconds(10),
			Duration.ofSeconds(60), Duration.ofSeconds(5), new ChatProperties.Cleanup(Duration.ofMinutes(10),
			Duration.ofHours(1), 500));
		hub = new ChatHub(container, JsonMapper.builder().build(), properties,
			Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
	}

	private ChatConnection open(String id) {
		WebSocketSession session = mock(WebSocketSession.class);
		given(session.getId()).willReturn(id);
		given(session.isOpen()).willReturn(true);
		return hub.open(session);
	}

	@Test
	void 방_구독이_실패하면_빈_방을_남기지_않아_다음_연결이_다시_구독한다() {
		doThrow(new RedisConnectionFailureException("down")).doNothing()
			.when(container).addMessageListener(eq(hub), any(Topic.class));

		ChatConnection first = open("s-1");
		assertThatThrownBy(() -> hub.join(first, 1L, 10L)).isInstanceOf(RedisConnectionFailureException.class);
		hub.closed("s-1");

		hub.join(open("s-2"), 2L, 10L);

		verify(container, times(2)).addMessageListener(hub, ROOM_TOPIC);
	}

	@Test
	void 같은_방_두_번째_연결은_다시_구독하지_않고_마지막_연결이_닫히면_푼다() {
		doNothing().when(container).addMessageListener(eq(hub), any(Topic.class));

		hub.join(open("s-1"), 1L, 10L);
		hub.join(open("s-2"), 2L, 10L);
		hub.closed("s-1");
		verify(container, times(0)).removeMessageListener(hub, ROOM_TOPIC);
		hub.closed("s-2");

		verify(container, times(1)).addMessageListener(hub, ROOM_TOPIC);
		verify(container, times(1)).removeMessageListener(hub, ROOM_TOPIC);
	}
}
