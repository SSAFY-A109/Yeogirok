package com.ssafy.yeogirok.domain.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.ssafy.yeogirok.domain.chat.cleanup.ChatCleanupJob;
import com.ssafy.yeogirok.domain.chat.service.ChatTicketService;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestAuthConfig;
import com.ssafy.yeogirok.support.TestChatConfig;
import com.ssafy.yeogirok.support.TestUsers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 서버·WebSocket·Redis Pub/Sub 으로 API-077 계약을 끝에서 끝까지 본다(AUTH → READY → SEND_MESSAGE → ACK·MESSAGE_CREATED,
 * PING/PONG, 오류 프레임, 나가기·방 삭제 연결 종료, 메시지 정리). DB 는 이 테스트 전용 H2 다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.datasource.url=jdbc:h2:mem:chat-ws;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
	"spring.sql.init.mode=never",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Import({TestAuthConfig.class, TestChatConfig.class})
class ChatWebSocketTest {

	private static final AtomicLong USER_IDS = new AtomicLong(9000);
	private static final long WAIT_SECONDS = 5;

	@LocalServerPort
	int port;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomMemberRepository roomMemberRepository;
	@Autowired
	ChatTicketService chatTicketService;
	@Autowired
	ChatHub chatHub;
	@Autowired
	ApplicationEventPublisher eventPublisher;
	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	ChatCleanupJob chatCleanupJob;
	@Autowired
	MutableClock clock;
	@Autowired
	JsonMapper jsonMapper;
	@Autowired
	DataSource dataSource;
	@Autowired
	StringRedisTemplate redis;

	/** 테스트마다 연 연결. 끝나면 닫아 다음 테스트의 연결 수·시간 초과 검사에 섞이지 않게 한다. */
	final List<Client> clients = new CopyOnWriteArrayList<>();
	JdbcTemplate jdbc;
	Long owner;
	Long friend;
	Long roomId;

	@BeforeEach
	void setUp() {
		clock.set(TestAuthConfig.START);
		jdbc = new JdbcTemplate(dataSource);
		owner = newUser();
		friend = newUser();
		roomId = roomService.create(owner, new RoomCreateCommand("채팅", null, null, TransportMode.CAR)).id();
		// 참여 API(API-025)는 MEMBERS 알림을 비동기로 보내 클라이언트가 연결된 뒤에 늦게 도착할 수 있다. 참여 행을 직접 넣는다.
		roomMemberRepository.save(RoomMember.joined(roomId, friend, clock.instant()));
	}

	@AfterEach
	void closeClients() throws Exception {
		for (Client client : clients) {
			if (client.session != null && client.session.isOpen()) {
				client.session.close();
			}
		}
		waitUntil(() -> chatHub.connectionCount() == 0);
		assertThat(chatHub.connectionCount()).isZero();
	}

	private Long newUser() {
		long id = USER_IDS.incrementAndGet();
		TestUsers.insertActive(jdbc, id);
		return id;
	}

	/* ── 클라이언트 ───────────────────────────────────────────── */

	/** 받은 프레임을 쌓아 두는 테스트 클라이언트. */
	class Client extends TextWebSocketHandler {

		final BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
		final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
		WebSocketSession session;

		Client connect() throws Exception {
			session = new StandardWebSocketClient()
				.execute(this, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws/v1"))
				.get(WAIT_SECONDS, TimeUnit.SECONDS);
			clients.add(this);
			return this;
		}

		/** 티켓을 받아 AUTH 하고 READY 를 확인한다. */
		Client authenticate(Long userId) throws Exception {
			connect();
			sendJson("{\"type\": \"AUTH\", \"ticket\": \"%s\"}".formatted(chatTicketService.issue(userId, roomId).ticket()));
			JsonNode ready = next();
			assertThat(ready.get("type").stringValue()).isEqualTo("READY");
			assertThat(ready.get("userId").longValue()).isEqualTo(userId);
			return this;
		}

		void sendJson(String json) throws Exception {
			session.sendMessage(new TextMessage(json));
		}

		void sendMessage(String clientMessageId, String body) throws Exception {
			sendJson(jsonMapper.writeValueAsString(java.util.Map.of("type", "SEND_MESSAGE", "roomId", roomId,
				"clientMessageId", clientMessageId, "body", body)));
		}

		JsonNode next() throws InterruptedException {
			JsonNode frame = frames.poll(WAIT_SECONDS, TimeUnit.SECONDS);
			assertThat(frame).as("프레임을 기다렸지만 오지 않았다").isNotNull();
			return frame;
		}

		/** type 이 맞는 다음 프레임. 그 사이의 다른 프레임은 버린다(ACK 와 MESSAGE_CREATED 의 도착 순서는 정하지 않는다). */
		JsonNode nextOfType(String type) throws InterruptedException {
			while (true) {
				JsonNode frame = next();
				if (type.equals(frame.get("type").stringValue())) {
					return frame;
				}
			}
		}

		CloseStatus awaitClose() throws Exception {
			return closed.get(WAIT_SECONDS, TimeUnit.SECONDS);
		}

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage message) {
			frames.add(jsonMapper.readTree(message.getPayload()));
		}

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
			closed.complete(status);
		}
	}

	private static void assertError(JsonNode frame, String code, boolean retryable) {
		assertThat(frame.get("type").stringValue()).isEqualTo("ERROR");
		assertThat(frame.get("code").stringValue()).isEqualTo(code);
		assertThat(frame.get("retryable").booleanValue()).isEqualTo(retryable);
	}

	private long messageCount() {
		Long count = jdbc.queryForObject("SELECT count(*) FROM room_messages WHERE room_id = ?", Long.class, roomId);
		return count == null ? 0 : count;
	}

	/* ── 테스트 ───────────────────────────────────────────────── */

	@Test
	void 보내면_보낸_사람은_ACK_를_받고_다른_참여자는_MESSAGE_CREATED_를_받는다() throws Exception {
		Client sender = new Client().authenticate(owner);
		Client receiver = new Client().authenticate(friend);

		sender.sendMessage("cm-1", "여기서 만나자!");

		JsonNode ack = sender.nextOfType("MESSAGE_ACK");
		assertThat(ack.get("replayed").booleanValue()).isFalse();
		assertThat(ack.get("clientMessageId").stringValue()).isEqualTo("cm-1");
		JsonNode created = receiver.nextOfType("MESSAGE_CREATED");
		assertThat(created.get("roomId").longValue()).isEqualTo(roomId);
		assertThat(created.get("message")).isEqualTo(ack.get("message"));
		assertThat(created.at("/message/sender/id").longValue()).isEqualTo(owner);
		assertThat(created.at("/message/body").stringValue()).isEqualTo("여기서 만나자!");
		assertThat(created.at("/message/createdAt").stringValue()).isEqualTo("2026-10-28 09:00:00+09");

		// 같은 clientMessageId·같은 본문 재전송은 같은 메시지 ACK(replayed) 이고 새로 방송하지 않는다.
		sender.sendMessage("cm-1", "여기서 만나자!");
		JsonNode replayed = sender.nextOfType("MESSAGE_ACK");
		assertThat(replayed.get("replayed").booleanValue()).isTrue();
		assertThat(replayed.at("/message/id")).isEqualTo(ack.at("/message/id"));
		assertThat(receiver.frames.poll(500, TimeUnit.MILLISECONDS)).isNull();
		assertThat(messageCount()).isEqualTo(1);
	}

	@Test
	void READY_와_PONG_은_최신_저장_ID_를_알려_준다() throws Exception {
		Client client = new Client().authenticate(owner);
		client.sendMessage("cm-1", "안녕");
		long id = client.nextOfType("MESSAGE_ACK").at("/message/id").longValue();

		client.sendJson("{\"type\": \"PING\"}");
		JsonNode pong = client.nextOfType("PONG");
		assertThat(pong.get("latestMessageId").longValue()).isEqualTo(id);

		Client late = new Client().connect();
		late.sendJson("{\"type\": \"AUTH\", \"ticket\": \"%s\"}".formatted(
			chatTicketService.issue(friend, roomId).ticket()));
		assertThat(late.nextOfType("READY").get("latestMessageId").longValue()).isEqualTo(id);
	}

	@Test
	void 입력_오류는_ERROR_만_보내고_연결은_유지한다() throws Exception {
		Client client = new Client().authenticate(owner);

		client.sendMessage("cm-1", "가".repeat(101));
		JsonNode tooLong = client.next();
		assertError(tooLong, "C003", false);
		assertThat(tooLong.get("clientMessageId").stringValue()).isEqualTo("cm-1");
		assertThat(tooLong.get("roomId").longValue()).isEqualTo(roomId);

		client.sendJson("{\"type\": \"SEND_MESSAGE\", \"roomId\": %d, \"body\": \"안녕\"}".formatted(roomId));
		JsonNode missing = client.next();
		assertError(missing, "C001", false);
		assertThat(missing.get("clientMessageId").isNull()).isTrue();

		client.sendJson("{\"type\": \"SEND_MESSAGE\", \"roomId\": 999999, \"clientMessageId\": \"x\", \"body\": \"a\"}");
		assertError(client.next(), "C003", false);
		client.sendJson("not json");
		assertError(client.next(), "C002", false);
		client.sendJson("{\"type\": \"HELLO\"}");
		assertError(client.next(), "C003", false);

		client.sendMessage("cm-2", "괜찮아");
		client.sendMessage("cm-2", "다른 본문");
		client.nextOfType("MESSAGE_ACK");
		assertError(client.nextOfType("ERROR"), "C005", false);

		client.sendJson("{\"type\": \"PING\"}");
		assertThat(client.nextOfType("PONG")).isNotNull();
		assertThat(messageCount()).isEqualTo(1);
	}

	@Test
	void 연결_요청에_잘못된_Bearer_헤더가_있어도_티켓으로_인증한다() throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.add("Authorization", "Bearer expired-or-invalid");
		Client client = new Client();
		client.session = new StandardWebSocketClient()
			.execute(client, headers, URI.create("ws://localhost:" + port + "/ws/v1"))
			.get(WAIT_SECONDS, TimeUnit.SECONDS);
		clients.add(client);

		client.sendJson("{\"type\": \"AUTH\", \"ticket\": \"%s\"}".formatted(
			chatTicketService.issue(owner, roomId).ticket()));
		assertThat(client.nextOfType("READY")).isNotNull();
	}

	@Test
	void 첫_프레임이_AUTH_가_아니면_A001_로_닫는다() throws Exception {
		Client client = new Client().connect();
		client.sendJson("{\"type\": \"PING\"}");

		assertError(client.next(), "A001", false);
		assertThat(client.awaitClose().getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
	}

	@Test
	void 없는_티켓과_이미_쓴_티켓은_A003_으로_닫는다() throws Exception {
		Client unknown = new Client().connect();
		unknown.sendJson("{\"type\": \"AUTH\", \"ticket\": \"%s\"}".formatted(UUID.randomUUID()));
		assertError(unknown.next(), "A003", false);
		unknown.awaitClose();

		String ticket = chatTicketService.issue(owner, roomId).ticket();
		Client first = new Client().connect();
		first.sendJson("{\"type\": \"AUTH\", \"ticket\": \"%s\"}".formatted(ticket));
		first.nextOfType("READY");
		Client reused = new Client().connect();
		reused.sendJson("{\"type\": \"AUTH\", \"ticket\": \"%s\"}".formatted(ticket));
		assertError(reused.next(), "A003", false);
		reused.awaitClose();
	}

	@Test
	void AUTH_대기_시간을_넘기면_A001_로_닫는다() throws Exception {
		Client client = new Client().connect();
		waitUntilConnections(1);

		clock.advance(Duration.ofSeconds(11));
		chatHub.closeTimedOut();

		assertError(client.next(), "A001", false);
		client.awaitClose();
	}

	@Test
	void 무응답_연결은_닫고_PING_을_보내는_연결은_유지한다() throws Exception {
		Client idle = new Client().authenticate(owner);
		Client active = new Client().authenticate(friend);

		clock.advance(Duration.ofSeconds(40));
		active.sendJson("{\"type\": \"PING\"}");
		active.nextOfType("PONG");
		clock.advance(Duration.ofSeconds(30));
		chatHub.closeTimedOut();

		idle.awaitClose();
		active.sendJson("{\"type\": \"PING\"}");
		assertThat(active.nextOfType("PONG")).isNotNull();
	}

	@Test
	void 나가면_그_사람의_연결은_RO002_방이_삭제되면_RO001_로_닫고_메시지를_정리한다() throws Exception {
		Client ownerClient = new Client().authenticate(owner);
		Client friendClient = new Client().authenticate(friend);
		ownerClient.sendMessage("cm-1", "하나");
		ownerClient.sendMessage("cm-2", "둘");
		ownerClient.sendMessage("cm-3", "셋");
		for (int i = 0; i < 3; i++) {
			ownerClient.nextOfType("MESSAGE_ACK");
		}

		roomService.leave(friend, roomId);
		assertError(friendClient.nextOfType("ERROR"), "RO002", false);
		friendClient.awaitClose();

		roomService.leave(owner, roomId);
		assertThat(ownerClient.nextOfType("ERROR").get("code").stringValue()).isIn("RO002", "RO001");
		ownerClient.awaitClose();

		// 방 삭제 커밋 직후 정리 대상으로 등록됐다. 한 번에 2개씩(테스트 설정) 나눠 모두 지운다.
		assertThat(messageCount()).isEqualTo(3);
		chatCleanupJob.cleanQueued();
		assertThat(messageCount()).isZero();
	}

	@Test
	void 정리_목록이_없어도_안전_스캔이_삭제된_방의_메시지를_지운다() throws Exception {
		Client ownerClient = new Client().authenticate(owner);
		ownerClient.sendMessage("cm-1", "하나");
		ownerClient.nextOfType("MESSAGE_ACK");
		jdbc.update("UPDATE travel_rooms SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", roomId);

		chatCleanupJob.scanDeletedRooms();

		assertThat(messageCount()).isZero();
	}

	@Test
	void 정리_목록의_잘못된_값은_빼고_나머지는_정리한다() throws Exception {
		Client ownerClient = new Client().authenticate(owner);
		ownerClient.sendMessage("cm-1", "하나");
		ownerClient.nextOfType("MESSAGE_ACK");
		jdbc.update("UPDATE travel_rooms SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", roomId);
		redis.opsForSet().add("chat-cleanup:rooms", "not-a-number", roomId.toString());

		chatCleanupJob.cleanQueued();

		assertThat(messageCount()).isZero();
		assertThat(redis.opsForSet().members("chat-cleanup:rooms")).isEmpty();
	}

	@Test
	void 여행방이_바뀌면_커밋_뒤_방에_연결된_모든_참여자가_ROOM_UPDATED_를_받는다() throws Exception {
		Client ownerClient = new Client().authenticate(owner);
		Client friendClient = new Client().authenticate(friend);

		int version = roomService.update(friend, roomId,
			new RoomUpdateCommand(1, "새 제목", false, null, null, null, false)).version();

		for (Client client : List.of(ownerClient, friendClient)) {
			JsonNode frame = client.nextOfType("ROOM_UPDATED");
			assertThat(frame.get("roomId").longValue()).isEqualTo(roomId);
			assertThat(frame.get("version").intValue()).isEqualTo(version);
			assertThat(frame.get("changes")).extracting(JsonNode::stringValue).containsExactly("ROOM");
			assertThat(frame.get("changedBy").longValue()).isEqualTo(friend);
		}

		// 실패한(롤백된) 변경은 알리지 않는다.
		assertThatThrownBy(() -> roomService.update(owner, roomId,
			new RoomUpdateCommand(1, "낡은 version", false, null, null, null, false)))
			.isInstanceOf(BusinessException.class);
		assertThat(ownerClient.frames.poll(500, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void 참여자가_나가면_남은_참여자는_MEMBERS_변경을_받는다() throws Exception {
		Client ownerClient = new Client().authenticate(owner);

		roomService.leave(friend, roomId);

		JsonNode frame = ownerClient.nextOfType("ROOM_UPDATED");
		assertThat(frame.get("changes")).extracting(JsonNode::stringValue).containsExactly("MEMBERS");
		assertThat(frame.get("changedBy").longValue()).isEqualTo(friend);
	}

	@Test
	void 회원이_탈퇴하면_참여_중이던_방의_다른_참여자는_MEMBERS_변경을_받는다() throws Exception {
		Client ownerClient = new Client().authenticate(owner);

		// 탈퇴 트랜잭션이 이벤트를 발행하고 커밋한다. 방 조회는 커밋 뒤 리스너(요청 스레드)에서 돈다.
		// 제공자 연결 해제는 TestAuthConfig 의 가짜 클라이언트가 받는다.
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(
			new UserWithdrawnEvent(friend, OAuthProvider.KAKAO, "kakao-" + friend, null)));

		JsonNode frame = ownerClient.nextOfType("ROOM_UPDATED");
		assertThat(frame.get("changes")).extracting(JsonNode::stringValue).containsExactly("MEMBERS");
		assertThat(frame.get("changedBy").longValue()).isEqualTo(friend);
	}

	@Test
	void 연결이_닫히면_방_구독에서도_빠진다() throws Exception {
		Client first = new Client().authenticate(owner);
		Client second = new Client().authenticate(friend);
		first.session.close();
		first.awaitClose();
		waitUntil(() -> chatHub.connectionCount() == 1);

		// 남은 연결은 그대로 받는다.
		second.sendMessage("cm-1", "남았니");
		assertThat(second.nextOfType("MESSAGE_CREATED").at("/message/body").stringValue()).isEqualTo("남았니");
	}

	private void waitUntilConnections(int atLeast) throws InterruptedException {
		waitUntil(() -> chatHub.connectionCount() >= atLeast);
	}

	private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
	}
}
