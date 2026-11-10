package com.ssafy.yeogirok.domain.chat.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 연결 하나. 인증(AUTH) 전에는 사용자·방이 없다. 인증 뒤에는 티켓의 방 하나에만 묶이며 바꾸지 않는다.
 *
 * <p>보내기는 연결마다 줄(큐)을 두고 공용 실행기에서 차례대로 비운다. 요청 처리 스레드(ACK·ERROR)와 Redis 수신
 * 스레드(MESSAGE_CREATED·강제 종료)가 넣은 순서대로 나가고, 느린 연결이 소켓 쓰기에서 막혀도 그 연결만 늦어진다.
 * 넣는 쪽은 소켓을 직접 건드리지 않는다(닫기도 실행기에서 한다).
 *
 * <p>방에 묶인 뒤 권한 재확인을 통과하기 전({@link #markReady} 전)에 온 방 메시지는 보내지 않고 잡아 둔다. 통과하면
 * READY 뒤에 보내고, 실패하면 버린다. 나간 사람에게 메시지가 새지 않게 한다.
 */
@Slf4j
@Getter
public final class ChatConnection {

	/** 보내지 못하고 쌓인 프레임 한도. 넘으면 연결을 닫는다(앱은 재연결 후 API-076 으로 복구한다). */
	static final int MAX_PENDING_FRAMES = 1_000;

	private final WebSocketSession session;
	private final Instant openedAt;
	private volatile Instant lastSeenAt;
	private volatile Long userId;
	private volatile Long roomId;

	private final Executor sendExecutor;
	private final Queue<Outbound> outbound = new ConcurrentLinkedQueue<>();
	private final AtomicInteger pending = new AtomicInteger();
	private final AtomicBoolean draining = new AtomicBoolean();
	private final AtomicBoolean closeRequested = new AtomicBoolean();

	/** READY 전에 온 방 메시지. readyLock 으로 지킨다. */
	private final Object readyLock = new Object();
	private List<String> beforeReady = new ArrayList<>();
	private volatile boolean ready;

	ChatConnection(WebSocketSession session, Instant now, Executor sendExecutor) {
		this.session = session;
		this.openedAt = now;
		this.lastSeenAt = now;
		this.sendExecutor = sendExecutor;
	}

	public String id() {
		return session.getId();
	}

	public boolean isAuthenticated() {
		return roomId != null;
	}

	void authenticate(Long userId, Long roomId) {
		this.userId = userId;
		this.roomId = roomId;
	}

	void touch(Instant now) {
		this.lastSeenAt = now;
	}

	/** 방 메시지(MESSAGE_CREATED). READY 전이면 잡아 둔다. */
	void deliver(String json) {
		if (!ready) {
			synchronized (readyLock) {
				if (!ready) {
					if (beforeReady.size() >= MAX_PENDING_FRAMES) {
						closeAsync(CloseStatus.SESSION_NOT_RELIABLE);
						return;
					}
					beforeReady.add(json);
					return;
				}
			}
		}
		send(json);
	}

	/** 권한 재확인을 통과했다. READY 를 보내고 그사이 잡아 둔 방 메시지를 이어서 보낸다. */
	void markReady(String readyJson) {
		synchronized (readyLock) {
			send(readyJson);
			beforeReady.forEach(this::send);
			beforeReady = List.of();
			ready = true;
		}
	}

	/** 줄에 넣고 바로 돌아온다. 이미 닫혔으면 버린다. */
	void send(String json) {
		enqueue(new Outbound(json, null));
	}

	/** 앞서 넣은 프레임(ERROR 등)을 모두 보낸 뒤 닫는다. */
	void close(CloseStatus status) {
		enqueue(new Outbound(null, status));
	}

	/**
	 * 줄을 기다리지 않고 닫는다(무응답·줄 넘침). 소켓 쓰기에서 막힌 연결도 닫히고, 여러 번 불러도 한 번만 닫는다.
	 * 부른 스레드가 아니라 실행기에서 닫아 넣는 쪽(Redis 수신·검사 스레드)이 막히지 않는다.
	 */
	void closeAsync(CloseStatus status) {
		if (closeRequested.compareAndSet(false, true)) {
			sendExecutor.execute(() -> closeNow(status));
		}
	}

	private void enqueue(Outbound item) {
		if (!session.isOpen() || closeRequested.get()) {
			return;
		}
		if (pending.incrementAndGet() > MAX_PENDING_FRAMES) {
			log.warn("채팅 연결이 받지 못한 프레임이 한도를 넘어 닫습니다. connection={}", id());
			closeAsync(CloseStatus.SESSION_NOT_RELIABLE);
			return;
		}
		outbound.add(item);
		if (draining.compareAndSet(false, true)) {
			sendExecutor.execute(this::drain);
		}
	}

	/** 한 번에 한 스레드만 비운다. 비우는 사이 새로 들어온 것이 있으면 다시 맡는다. */
	private void drain() {
		do {
			Outbound item;
			while ((item = outbound.poll()) != null) {
				pending.decrementAndGet();
				if (item.closeStatus() != null) {
					closeNow(item.closeStatus());
				} else {
					write(item.json());
				}
			}
			draining.set(false);
		} while (!outbound.isEmpty() && draining.compareAndSet(false, true));
	}

	private void write(String json) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.sendMessage(new TextMessage(json));
		} catch (IOException | RuntimeException e) {
			log.debug("채팅 프레임을 보내지 못해 연결을 닫습니다. connection={}", id(), e);
			closeNow(CloseStatus.SESSION_NOT_RELIABLE);
		}
	}

	private void closeNow(CloseStatus status) {
		try {
			session.close(status);
		} catch (IOException | RuntimeException e) {
			log.debug("채팅 연결을 닫지 못했습니다. connection={}", id(), e);
		}
	}

	/** 보낼 JSON 또는 닫기 지시 중 하나. */
	private record Outbound(String json, CloseStatus closeStatus) {
	}
}
