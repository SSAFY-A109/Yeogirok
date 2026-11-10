package com.ssafy.yeogirok.domain.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** 연결별 보내기 줄: 순서, READY 전 보류, 넣는 스레드가 소켓을 건드리지 않음. */
class ChatConnectionTest {

	WebSocketSession session;
	/** 실행을 미뤘다가 테스트가 직접 돌린다. 넣는 스레드에서 소켓 작업이 일어나지 않는지 본다. */
	List<Runnable> tasks;
	ChatConnection connection;

	@BeforeEach
	void setUp() {
		session = mock(WebSocketSession.class);
		given(session.isOpen()).willReturn(true);
		given(session.getId()).willReturn("s-1");
		tasks = new ArrayList<>();
		Executor deferred = tasks::add;
		connection = new ChatConnection(session, Instant.EPOCH, deferred);
		connection.authenticate(1L, 10L);
	}

	private void runTasks() {
		while (!tasks.isEmpty()) {
			tasks.removeFirst().run();
		}
	}

	@Test
	void 넣은_순서대로_보내고_닫기는_앞선_프레임_뒤에_한다() throws Exception {
		connection.send("a");
		connection.send("b");
		connection.close(CloseStatus.POLICY_VIOLATION);
		verify(session, never()).sendMessage(any());

		runTasks();

		InOrder order = inOrder(session);
		order.verify(session).sendMessage(new TextMessage("a"));
		order.verify(session).sendMessage(new TextMessage("b"));
		order.verify(session).close(CloseStatus.POLICY_VIOLATION);
	}

	@Test
	void READY_전에_온_방_메시지는_잡아_뒀다가_READY_뒤에_보낸다() throws Exception {
		connection.deliver("m1");
		runTasks();
		verify(session, never()).sendMessage(any());

		connection.markReady("ready");
		connection.deliver("m2");
		runTasks();

		InOrder order = inOrder(session);
		order.verify(session).sendMessage(new TextMessage("ready"));
		order.verify(session).sendMessage(new TextMessage("m1"));
		order.verify(session).sendMessage(new TextMessage("m2"));
	}

	@Test
	void 줄이_넘치면_넣는_스레드가_아니라_실행기에서_한_번만_닫는다() throws Exception {
		for (int i = 0; i <= ChatConnection.MAX_PENDING_FRAMES; i++) {
			connection.send("x");
		}
		connection.closeAsync(CloseStatus.SESSION_NOT_RELIABLE);
		verify(session, never()).close(any());

		runTasks();

		verify(session, times(1)).close(CloseStatus.SESSION_NOT_RELIABLE);
	}

	@Test
	void 즉시_닫기는_보내기_줄을_기다리지_않는다() throws Exception {
		connection.send("a");
		connection.closeAsync(CloseStatus.SESSION_NOT_RELIABLE);

		// 줄 비우기(첫 작업)보다 닫기(둘째 작업)를 먼저 돌려도 닫힌다.
		tasks.get(1).run();

		verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
		assertThat(tasks).hasSize(2);
	}
}
