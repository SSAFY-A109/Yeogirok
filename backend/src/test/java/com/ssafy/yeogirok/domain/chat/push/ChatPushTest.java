package com.ssafy.yeogirok.domain.chat.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.ssafy.yeogirok.domain.chat.push.ChatPushService.ChatPushJob;
import com.ssafy.yeogirok.domain.chat.realtime.ChatHub;
import com.ssafy.yeogirok.domain.chat.service.ChatTicketService;
import com.ssafy.yeogirok.domain.push.sender.PushResult;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.service.RoomInvitationService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.support.FakePushSender;
import com.ssafy.yeogirok.support.FakePushSender.Sent;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestAuthConfig;
import com.ssafy.yeogirok.support.TestChatConfig;
import com.ssafy.yeogirok.support.TestPushConfig;
import com.ssafy.yeogirok.support.TestUsers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 채팅 앱 푸시(노션 API-077 채팅 푸시)를 실제 서버·WebSocket·Redis 로 본다. FCM 은 가짜 발송기다.
 *
 * <p>받는 사람 고르기는 {@link ChatPushService#deliver} 를 직접 불러 바로 확인하고, WebSocket 에서 실제로 비동기 발송이
 * 일어나는지는 SEND_MESSAGE 로 본다. 수신자·방은 테스트마다 새로 만들어 Redis 키(발송 제한·접속 상태)가 섞이지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.datasource.url=jdbc:h2:mem:chat-push;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
	"spring.sql.init.mode=never",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	// 운영 값(3초)은 짧아 테스트가 느리면 제한이 풀린다. 제한 동작만 보도록 길게 둔다.
	"yeogirok.chat.push.throttle=PT1M"
})
@ActiveProfiles("test")
@Import({TestAuthConfig.class, TestChatConfig.class, TestPushConfig.class})
class ChatPushTest {

	private static final AtomicLong USER_IDS = new AtomicLong(19000);
	private static final AtomicLong MESSAGE_IDS = new AtomicLong(1);
	private static final long WAIT_SECONDS = 5;
	/** 오지 않아야 하는 발송을 기다리는 시간. */
	private static final long QUIET_MILLIS = 700;

	@LocalServerPort
	int port;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	ChatTicketService chatTicketService;
	@Autowired
	ChatPushService chatPushService;
	@Autowired
	ChatHub chatHub;
	@Autowired
	FakePushSender pushSender;
	@Autowired
	ChatPresence presence;
	@Autowired
	MutableClock clock;
	@Autowired
	JsonMapper jsonMapper;
	@Autowired
	DataSource dataSource;

	final List<Client> clients = new CopyOnWriteArrayList<>();
	JdbcTemplate jdbc;
	Long owner;
	Long friend;
	Long roomId;

	@BeforeEach
	void setUp() {
		clock.set(TestAuthConfig.START);
		pushSender.reset();
		jdbc = new JdbcTemplate(dataSource);
		owner = newUser();
		friend = newUser();
		roomId = roomService.create(owner, new RoomCreateCommand("제주 3박4일", null, null, TransportMode.CAR)).id();
		join(friend);
	}

	@AfterEach
	void closeClients() throws Exception {
		for (Client client : clients) {
			if (client.session != null && client.session.isOpen()) {
				client.session.close();
			}
		}
		waitUntil(() -> chatHub.connectionCount() == 0);
	}

	/* ── 헬퍼 ─────────────────────────────────────────────────── */

	private Long newUser() {
		long id = USER_IDS.incrementAndGet();
		TestUsers.insertActive(jdbc, id);
		return id;
	}

	private void join(Long userId) {
		String token = roomInvitationService.getOrCreateInvitation(owner, roomId).inviteToken();
		roomInvitationService.accept(userId, token);
	}

	/** 기기 토큰을 바로 넣는다(API-095 는 PushTokenApiTest 가 본다). */
	private String registerDevice(Long userId) {
		String token = "fcm-" + userId + "-" + MESSAGE_IDS.incrementAndGet();
		Instant now = clock.instant();
		jdbc.update("INSERT INTO user_push_tokens (user_id, token, platform, created_at, updated_at)"
			+ " VALUES (?, ?, 'ANDROID', ?, ?)", userId, token, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
		return token;
	}

	private List<Long> ownersOf(String token) {
		return jdbc.queryForList("SELECT user_id FROM user_push_tokens WHERE token = ?", Long.class, token);
	}

	/** owner 가 보낸 새 메시지 하나의 푸시를 이 스레드에서 끝까지 처리한다. */
	private void deliverFromOwner(String body) {
		chatPushService.deliver(new ChatPushJob(roomId, owner, "보경", MESSAGE_IDS.incrementAndGet(), body));
	}

	/** 지금까지 보낸 토큰 전부. */
	private List<String> sentTokens() {
		return pushSender.drain().stream().flatMap(sent -> sent.tokens().stream()).toList();
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
	}

	/** 받은 프레임을 쌓아 두는 테스트 클라이언트. */
	class Client extends TextWebSocketHandler {

		final BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
		WebSocketSession session;

		Client authenticate(Long userId) throws Exception {
			session = new StandardWebSocketClient()
				.execute(this, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws/v1"))
				.get(WAIT_SECONDS, TimeUnit.SECONDS);
			clients.add(this);
			sendJson("{\"type\": \"AUTH\", \"ticket\": \"%s\"}".formatted(chatTicketService.issue(userId, roomId).ticket()));
			assertThat(nextOfType("READY").get("userId").longValue()).isEqualTo(userId);
			return this;
		}

		void sendJson(String json) throws Exception {
			session.sendMessage(new TextMessage(json));
		}

		void sendMessage(String clientMessageId, String body) throws Exception {
			sendJson(jsonMapper.writeValueAsString(Map.of("type", "SEND_MESSAGE", "roomId", roomId,
				"clientMessageId", clientMessageId, "body", body)));
		}

		JsonNode nextOfType(String type) throws InterruptedException {
			while (true) {
				JsonNode frame = frames.poll(WAIT_SECONDS, TimeUnit.SECONDS);
				assertThat(frame).as(type + " 프레임을 기다렸지만 오지 않았다").isNotNull();
				if (type.equals(frame.get("type").stringValue())) {
					return frame;
				}
			}
		}

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage message) {
			frames.add(jsonMapper.readTree(message.getPayload()));
		}
	}

	/* ── WebSocket 에서 비동기 발송 ───────────────────────────── */

	@Test
	void 새_메시지를_보내면_연결되지_않은_참여자의_모든_기기로_푸시한다() throws Exception {
		String phone = registerDevice(friend);
		String tablet = registerDevice(friend);
		registerDevice(owner); // 보낸 사람 기기에는 보내지 않는다
		Client sender = new Client().authenticate(owner);

		sender.sendMessage("cm-1", "내일 공항에서 9시에 만나요!");
		long messageId = sender.nextOfType("MESSAGE_ACK").at("/message/id").longValue();

		Sent sent = pushSender.poll(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
		assertThat(sent).as("푸시가 나가야 한다").isNotNull();
		assertThat(sent.tokens()).containsExactly(phone, tablet);
		assertThat(sent.message().title()).isEqualTo("제주 3박4일");
		assertThat(sent.message().body()).isEqualTo("테스트: 내일 공항에서 9시에 만나요!");
		assertThat(sent.message().data()).isEqualTo(
			Map.of("type", "CHAT", "roomId", roomId.toString(), "messageId", Long.toString(messageId)));
		assertThat(sent.message().channelId()).isEqualTo("chat");
		assertThat(sent.message().tag()).isEqualTo("chat:" + roomId);
		assertThat(pushSender.poll(QUIET_MILLIS)).isNull();
	}

	@Test
	void 같은_메시지의_재전송_ACK_에는_푸시하지_않는다() throws Exception {
		registerDevice(friend);
		Client sender = new Client().authenticate(owner);
		sender.sendMessage("cm-1", "안녕");
		sender.nextOfType("MESSAGE_ACK");
		assertThat(pushSender.poll(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isNotNull();

		sender.sendMessage("cm-1", "안녕");
		assertThat(sender.nextOfType("MESSAGE_ACK").get("replayed").booleanValue()).isTrue();

		assertThat(pushSender.poll(QUIET_MILLIS)).isNull();
	}

	@Test
	void 발송이_실패해도_ACK_와_실시간_전달은_그대로다() throws Exception {
		registerDevice(friend);
		pushSender.onSend(tokens -> {
			throw new IllegalStateException("FCM 장애");
		});
		Client sender = new Client().authenticate(owner);
		Client other = new Client().authenticate(newMemberWithoutDevice());

		sender.sendMessage("cm-1", "안녕");

		assertThat(sender.nextOfType("MESSAGE_ACK").get("replayed").booleanValue()).isFalse();
		assertThat(other.nextOfType("MESSAGE_CREATED").at("/message/body").stringValue()).isEqualTo("안녕");
		sender.sendMessage("cm-2", "또 안녕");
		assertThat(sender.nextOfType("MESSAGE_ACK").at("/message/body").stringValue()).isEqualTo("또 안녕");
	}

	private Long newMemberWithoutDevice() {
		Long member = newUser();
		join(member);
		return member;
	}

	/* ── 받는 사람 고르기 ─────────────────────────────────────── */

	@Test
	void 그_방에_READY_로_연결된_사람은_빼고_연결을_닫으면_다시_보낸다() throws Exception {
		String friendDevice = registerDevice(friend);
		Long third = newUser();
		join(third);
		String thirdDevice = registerDevice(third);
		Client watching = new Client().authenticate(friend);

		deliverFromOwner("첫 메시지");
		assertThat(sentTokens()).containsExactly(thirdDevice);

		watching.session.close();
		// 닫힘 처리는 연결 목록에서 뺀 뒤 접속 기록을 지운다. 기록이 지워질 때까지 기다린다.
		waitUntil(() -> presence.onlineUsers(roomId).isEmpty());
		deliverFromOwner("두 번째 메시지");
		// third 는 발송 제한에 걸리고, 화면을 나간 friend 는 받는다.
		assertThat(sentTokens()).containsExactly(friendDevice);
	}

	@Test
	void 같은_수신자_같은_방에는_제한_시간_안에_한_번만_보낸다() {
		String friendDevice = registerDevice(friend);

		deliverFromOwner("하나");
		deliverFromOwner("둘");
		deliverFromOwner("셋");

		assertThat(sentTokens()).containsExactly(friendDevice);
	}

	@Test
	void 나간_사람과_탈퇴한_사람에게는_보내지_않는다() {
		registerDevice(friend);
		Long withdrawn = newUser();
		join(withdrawn);
		registerDevice(withdrawn);
		Long staying = newUser();
		join(staying);
		String stayingDevice = registerDevice(staying);

		roomService.leave(friend, roomId);
		jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", withdrawn);
		deliverFromOwner("남은 사람만");

		assertThat(sentTokens()).containsExactly(stayingDevice);
	}

	@Test
	void 기기_토큰이_없으면_보내지_않는다() {
		deliverFromOwner("아무도 없음");

		assertThat(sentTokens()).isEmpty();
	}

	@Test
	void 한_기기에도_보내지_못하면_제한을_풀어_다음_메시지에서_다시_보낸다() {
		String device = registerDevice(friend);
		pushSender.resultFor(device, PushResult.FAILED);
		deliverFromOwner("하나");

		pushSender.resultFor(device, PushResult.SENT);
		deliverFromOwner("둘");
		deliverFromOwner("셋");

		// 실패한 "하나" 는 제한을 풀었고, 보낸 "둘" 은 제한을 잡아 "셋" 은 생략된다.
		assertThat(sentTokens()).containsExactly(device, device);
	}

	@Test
	void FCM_장애면_제한을_풀지_않는다() {
		String device = registerDevice(friend);
		pushSender.resultFor(device, PushResult.UNAVAILABLE);
		deliverFromOwner("하나");

		pushSender.resultFor(device, PushResult.SENT);
		deliverFromOwner("둘");

		// 장애 중 메시지마다 다시 보내지 않도록 "하나" 의 제한이 남아 "둘" 은 생략된다.
		assertThat(sentTokens()).containsExactly(device);
	}

	@Test
	void 종료로_인터럽트되면_남은_수신자에게는_보내지_않는다() {
		String friendDevice = registerDevice(friend);
		Long third = newUser();
		join(third);
		registerDevice(third);
		pushSender.onSend(tokens -> Thread.currentThread().interrupt());
		try {
			deliverFromOwner("안녕");
		} finally {
			Thread.interrupted();
		}

		assertThat(sentTokens()).containsExactly(friendDevice);
	}

	@Test
	void UNREGISTERED_토큰은_지우고_다른_토큰은_둔다() {
		String gone = registerDevice(friend);
		String alive = registerDevice(friend);
		pushSender.resultFor(gone, PushResult.UNREGISTERED);
		pushSender.resultFor(alive, PushResult.FAILED);

		deliverFromOwner("안녕");

		assertThat(ownersOf(gone)).isEmpty();
		assertThat(ownersOf(alive)).containsExactly(friend);
	}

	@Test
	void UNREGISTERED_라도_그사이_다른_계정으로_옮겨_간_토큰은_지우지_않는다() {
		String token = registerDevice(friend);
		Long newOwner = newUser();
		pushSender.resultFor(token, PushResult.UNREGISTERED);
		// 발송 중에 같은 기기에서 다른 계정으로 로그인해 토큰이 옮겨 갔다.
		pushSender.onSend(tokens -> jdbc.update("UPDATE user_push_tokens SET user_id = ? WHERE token = ?", newOwner,
			token));

		deliverFromOwner("안녕");

		assertThat(ownersOf(token)).containsExactly(newOwner);
	}
}
