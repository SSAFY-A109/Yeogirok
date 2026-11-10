package com.ssafy.yeogirok.domain.chat.realtime;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.ssafy.yeogirok.domain.chat.ChatProperties;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이 서버에 열린 채팅 연결과 Redis 구독.
 *
 * <ul>
 *   <li>방별 채널은 이 서버에 그 방 연결이 하나라도 있을 때만 구독하고, 마지막 연결이 닫히면 푼다. 구독·해제는 방 단위
 *       잠금(줄무늬) 아래에서 해 같은 방의 구독과 해제가 뒤바뀌지 않게 하고, 전달 경로는 이 잠금을 쓰지 않는다.</li>
 *   <li>Redis 수신은 한 스레드로 받은 순서대로 각 연결의 보내기 줄에 넣기만 한다({@link ChatConnection}). 권한 재확인 전
 *       (READY 전) 연결에 온 방 메시지는 잡아 뒀다가 통과하면 보낸다. 실제 쓰기는
 *       연결마다 따로 해 느린 연결 하나가 다른 방·연결 전달을 막지 않는다. 서로 다른 요청이 커밋 직후 발행해 생기는
 *       순서 역전은 앱이 message.id 로 정렬해 맞춘다(11/10 결정).</li>
 *   <li>나가기·방 삭제·탈퇴는 {@code chat:control} 로 받아 맞는 연결에 ERROR 를 보내고 닫는다.</li>
 *   <li>AUTH 를 기다리는 시간과 인증된 연결의 무응답 시간을 주기적으로 검사해 넘긴 연결을 닫는다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHub implements MessageListener {

	/** 방 구독·해제 잠금 수. 방 ID 로 나눠 서로 다른 방은 거의 기다리지 않는다. */
	private static final int ROOM_LOCK_STRIPES = 64;

	private final RedisMessageListenerContainer chatListenerContainer;
	private final JsonMapper jsonMapper;
	private final ChatProperties properties;
	private final Clock clock;

	private final Map<String, ChatConnection> connections = new ConcurrentHashMap<>();
	private final Map<Long, Set<ChatConnection>> rooms = new ConcurrentHashMap<>();
	private final Object[] roomLocks = newLocks();
	/** 연결별 보내기 줄을 비우는 실행기. 소켓 쓰기에서 막혀도 되도록 가상 스레드를 쓴다. */
	private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

	@PostConstruct
	void subscribeControl() {
		chatListenerContainer.addMessageListener(this, new ChannelTopic(ChatPublisher.CONTROL_CHANNEL));
	}

	@PreDestroy
	void shutdown() {
		sendExecutor.shutdown();
	}

	ChatConnection open(WebSocketSession session) {
		ChatConnection connection = new ChatConnection(session, clock.instant(), sendExecutor);
		connections.put(connection.id(), connection);
		return connection;
	}

	ChatConnection get(String sessionId) {
		return connections.get(sessionId);
	}

	/**
	 * AUTH 를 통과한 연결을 방에 묶는다. 이 서버의 그 방 첫 연결이면 방 채널을 구독한다.
	 * 그사이 연결이 닫혔으면(닫힘 처리가 먼저 끝났으면) 바로 되돌려 방 목록·구독이 남지 않게 한다.
	 */
	void join(ChatConnection connection, Long userId, Long roomId) {
		connection.authenticate(userId, roomId);
		synchronized (lockOf(roomId)) {
			Set<ChatConnection> members = rooms.get(roomId);
			if (members == null) {
				// 구독에 성공한 뒤에만 방을 맵에 넣는다. 실패한 채 빈 방이 남으면 다음 연결이 구독을 건너뛰어
				// 이 서버에서 그 방의 실시간 메시지가 끊긴다. 실패하면 예외가 그대로 나가 이 연결은 C014 로 닫힌다.
				chatListenerContainer.addMessageListener(this, roomTopic(roomId));
				members = ConcurrentHashMap.newKeySet();
				rooms.put(roomId, members);
			}
			members.add(connection);
		}
		if (!connections.containsKey(connection.id())) {
			leaveRoom(connection);
		}
	}

	void closed(String sessionId) {
		ChatConnection connection = connections.remove(sessionId);
		if (connection != null && connection.isAuthenticated()) {
			leaveRoom(connection);
		}
	}

	/** 여러 번 불려도 된다. */
	private void leaveRoom(ChatConnection connection) {
		Long roomId = connection.getRoomId();
		synchronized (lockOf(roomId)) {
			Set<ChatConnection> members = rooms.get(roomId);
			if (members == null || !members.remove(connection) || !members.isEmpty()) {
				return;
			}
			rooms.remove(roomId);
			chatListenerContainer.removeMessageListener(this, roomTopic(roomId));
		}
	}

	/** 이 서버에 열린 연결 수(테스트·운영 확인용). */
	public int connectionCount() {
		return connections.size();
	}

	/* ── 보내기 ─────────────────────────────────────────────── */

	void send(ChatConnection connection, Object frame) {
		connection.send(jsonMapper.writeValueAsString(frame));
	}

	/** 권한 재확인을 통과한 연결에 READY 를 보내고, 그때부터 방 메시지를 바로 보낸다. */
	void ready(ChatConnection connection, ChatFrames.Ready frame) {
		connection.markReady(jsonMapper.writeValueAsString(frame));
	}

	/** ERROR 를 보내고 닫는다. 인증 실패·권한 상실 때 쓴다. */
	void reject(ChatConnection connection, Long roomId, String clientMessageId, ExceptionType type, String message) {
		send(connection, ChatFrames.Error.of(roomId, clientMessageId, type, message));
		connection.close(CloseStatus.POLICY_VIOLATION);
	}

	/* ── Redis 수신 ─────────────────────────────────────────── */

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		try {
			if (channel.equals(ChatPublisher.CONTROL_CHANNEL)) {
				kick(jsonMapper.readValue(body, ChatKick.class));
			} else if (channel.startsWith(ChatPublisher.ROOM_CHANNEL_PREFIX)) {
				Long roomId = Long.valueOf(channel.substring(ChatPublisher.ROOM_CHANNEL_PREFIX.length()));
				Set<ChatConnection> members = rooms.get(roomId);
				if (members != null) {
					members.forEach(connection -> connection.deliver(body));
				}
			}
		} catch (RuntimeException e) {
			log.error("채팅 Redis 메시지를 처리하지 못했습니다. channel={}", channel, e);
		}
	}

	private void kick(ChatKick kick) {
		for (ChatConnection connection : connections.values()) {
			if (kick.matches(connection)) {
				reject(connection, connection.getRoomId(), null, kick.reason(), kick.reason().getMessage());
			}
		}
	}

	/* ── 시간 초과 검사 ─────────────────────────────────────── */

	@Scheduled(fixedDelayString = "${yeogirok.chat.sweep-interval}", initialDelayString = "${yeogirok.chat.sweep-interval}")
	public void closeTimedOut() {
		Instant now = clock.instant();
		for (ChatConnection connection : connections.values()) {
			if (!connection.isAuthenticated()) {
				if (connection.getOpenedAt().plus(properties.authTimeout()).isBefore(now)) {
					reject(connection, null, null, ExceptionType.ACCESS_TOKEN_MISSING, "AUTH 대기 시간을 넘겼습니다.");
				}
			} else if (connection.getLastSeenAt().plus(properties.idleTimeout()).isBefore(now)) {
				// 보내기 줄을 기다리지 않는다. 쓰기에서 막힌 연결도 닫힌다.
				connection.closeAsync(CloseStatus.SESSION_NOT_RELIABLE);
			}
		}
	}

	private Object lockOf(Long roomId) {
		return roomLocks[Math.floorMod(roomId.hashCode(), ROOM_LOCK_STRIPES)];
	}

	private static Object[] newLocks() {
		Object[] locks = new Object[ROOM_LOCK_STRIPES];
		for (int i = 0; i < locks.length; i++) {
			locks[i] = new Object();
		}
		return locks;
	}

	private static ChannelTopic roomTopic(Long roomId) {
		return new ChannelTopic(ChatPublisher.ROOM_CHANNEL_PREFIX + roomId);
	}
}
