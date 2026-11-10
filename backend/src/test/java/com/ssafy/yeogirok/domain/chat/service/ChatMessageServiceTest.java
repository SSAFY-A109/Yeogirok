package com.ssafy.yeogirok.domain.chat.service;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.chat.dto.ChatMessage;
import com.ssafy.yeogirok.domain.chat.dto.ChatMessagePage;
import com.ssafy.yeogirok.domain.chat.dto.ChatReadResponse;
import com.ssafy.yeogirok.domain.chat.entity.RoomMessage;
import com.ssafy.yeogirok.domain.chat.service.ChatMessageService.SendResult;
import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.service.RoomInvitationService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestChatConfig;
import com.ssafy.yeogirok.support.TestUsers;

@DataJpaTest
@Import({RoomTestConfig.class, ChatMessageService.class, ChatReadService.class, TestChatConfig.class})
class ChatMessageServiceTest {

	static final Long OWNER = 3001L;
	static final Long FRIEND = 3002L;
	static final Long STRANGER = 3003L;

	@Autowired
	ChatMessageService chatMessageService;
	@Autowired
	ChatReadService chatReadService;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	Long roomId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		TestUsers.insertActive(em.getEntityManager(), OWNER, FRIEND, STRANGER);
		roomId = createRoom(OWNER);
		join(FRIEND, roomId);
	}

	private Long createRoom(Long userId) {
		return roomService.create(userId, new RoomCreateCommand("채팅방", null, null, TransportMode.CAR)).id();
	}

	private void join(Long userId, Long room) {
		String token = roomInvitationService.getOrCreateInvitation(OWNER, room).inviteToken();
		roomInvitationService.accept(userId, token);
	}

	private Long send(Long userId, String body) {
		return chatMessageService.send(userId, roomId, "cm-" + body, body).message().id();
	}

	private void withdraw(Long userId) {
		em.getEntityManager().createNativeQuery("UPDATE users SET status = 'WITHDRAWN', nickname = '탈퇴한 사용자'"
			+ " WHERE id = ?1").setParameter(1, userId).executeUpdate();
		em.clear();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	private static List<String> bodies(ChatMessagePage page) {
		return page.items().stream().map(ChatMessage::body).toList();
	}

	/* ── 전송 ─────────────────────────────────────────────────── */

	@Test
	void 보내면_저장하고_보낸_사람_표시와_서버_시각을_돌려준다() {
		SendResult result = chatMessageService.send(OWNER, roomId, "cm-1", " 여기서 만나자! ");

		assertThat(result.replayed()).isFalse();
		ChatMessage message = ChatMessage.from(result.message());
		assertThat(message.sender()).isEqualTo(new ChatMessage.Sender(OWNER, "테스트"));
		assertThat(message.body()).isEqualTo(" 여기서 만나자! ");
		assertThat(message.createdAt()).isEqualTo(NOW);
		assertThat(message.clientMessageId()).isEqualTo("cm-1");
	}

	@Test
	void 같은_clientMessageId_같은_본문은_새로_저장하지_않고_원래_메시지를_돌려준다() {
		Long first = chatMessageService.send(OWNER, roomId, "cm-1", "안녕").message().id();

		SendResult again = chatMessageService.send(OWNER, roomId, "cm-1", "안녕");

		assertThat(again.replayed()).isTrue();
		assertThat(again.message().id()).isEqualTo(first);
		assertThat(em.getEntityManager().createQuery("select count(m) from RoomMessage m", Long.class)
			.getSingleResult()).isEqualTo(1L);
	}

	@Test
	void 같은_clientMessageId_다른_본문은_C005() {
		chatMessageService.send(OWNER, roomId, "cm-1", "안녕");

		assertError(() -> chatMessageService.send(OWNER, roomId, "cm-1", "다른 말"), ExceptionType.IDEMPOTENCY_KEY_REUSED);
	}

	@Test
	void 같은_문장도_clientMessageId_가_다르면_새_메시지다() {
		Long first = chatMessageService.send(OWNER, roomId, "cm-1", "ㅋㅋ").message().id();
		Long second = chatMessageService.send(OWNER, roomId, "cm-2", "ㅋㅋ").message().id();
		// 다른 사람의 같은 clientMessageId 도 다른 메시지다.
		Long third = chatMessageService.send(FRIEND, roomId, "cm-1", "ㅋㅋ").message().id();

		assertThat(List.of(first, second, third)).doesNotHaveDuplicates();
	}

	@Test
	void 본문은_100자까지_빈_값과_공백만은_C003() {
		String hundred = "가".repeat(RoomMessage.BODY_MAX_LENGTH);
		assertThat(chatMessageService.send(OWNER, roomId, "cm-ok", hundred).message().body()).isEqualTo(hundred);
		// 이모지 하나는 1자다(PostgreSQL char_length 와 같은 코드 포인트 기준). H2 CHECK 는 UTF-16 으로 세어 검사만 따로 본다.
		ChatMessageService.validateBody("😀".repeat(RoomMessage.BODY_MAX_LENGTH));
		assertError(() -> ChatMessageService.validateBody("😀".repeat(RoomMessage.BODY_MAX_LENGTH + 1)),
			ExceptionType.NOT_ALLOWED_VALUE);

		assertError(() -> chatMessageService.send(OWNER, roomId, "cm-a", "가".repeat(101)),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatMessageService.send(OWNER, roomId, "cm-b", ""), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatMessageService.send(OWNER, roomId, "cm-c", "  \n "), ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void clientMessageId_는_1에서_100자() {
		assertError(() -> chatMessageService.send(OWNER, roomId, "", "안녕"), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatMessageService.send(OWNER, roomId, "x".repeat(101), "안녕"),
			ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 참여자가_아니면_RO002_나간_사람도_RO002_삭제된_방은_RO001() {
		assertError(() -> chatMessageService.send(STRANGER, roomId, "cm-1", "안녕"), ExceptionType.ROOM_NOT_MEMBER);

		roomService.leave(FRIEND, roomId);
		assertError(() -> chatMessageService.send(FRIEND, roomId, "cm-1", "안녕"), ExceptionType.ROOM_NOT_MEMBER);

		roomService.leave(OWNER, roomId);
		assertError(() -> chatMessageService.send(OWNER, roomId, "cm-1", "안녕"), ExceptionType.ROOM_NOT_FOUND);
	}

	@Test
	void 탈퇴한_회원은_보낼_수_없다_A003() {
		withdraw(FRIEND);

		assertError(() -> chatMessageService.send(FRIEND, roomId, "cm-1", "안녕"), ExceptionType.ACCESS_TOKEN_INVALID);
		assertError(() -> chatMessageService.checkAccess(FRIEND, roomId), ExceptionType.ACCESS_TOKEN_INVALID);
	}

	@Test
	void 연결_권한_확인은_최신_ID_를_주고_참여자_아님_RO002_나감_RO002_삭제_RO001() {
		assertThat(chatMessageService.checkAccess(OWNER, roomId)).isNull();
		Long id = send(OWNER, "1");
		assertThat(chatMessageService.checkAccess(FRIEND, roomId)).isEqualTo(id);

		assertError(() -> chatMessageService.checkAccess(STRANGER, roomId), ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> chatMessageService.checkAccess(OWNER, 999_999L), ExceptionType.ROOM_NOT_FOUND);
		roomService.leave(FRIEND, roomId);
		em.flush();
		em.clear();
		assertError(() -> chatMessageService.checkAccess(FRIEND, roomId), ExceptionType.ROOM_NOT_MEMBER);
		roomService.leave(OWNER, roomId);
		em.flush();
		em.clear();
		assertError(() -> chatMessageService.checkAccess(OWNER, roomId), ExceptionType.ROOM_NOT_FOUND);
	}

	/* ── API-076 이력 ─────────────────────────────────────────── */

	@Test
	void 최근_이력은_최신_limit_개를_오래된_순으로_주고_beforeMessageId_로_이전_페이지를_읽는다() {
		List<Long> ids = List.of("1", "2", "3", "4", "5").stream().map(body -> send(OWNER, body)).toList();

		ChatMessagePage latest = chatMessageService.getMessages(FRIEND, roomId, null, null, null, 2);
		assertThat(bodies(latest)).containsExactly("4", "5");
		assertThat(latest.hasMore()).isTrue();
		assertThat(latest.nextAfterMessageId()).isNull();
		assertThat(latest.replayThroughMessageId()).isNull();

		ChatMessagePage older = chatMessageService.getMessages(FRIEND, roomId, ids.get(3), null, null, 2);
		assertThat(bodies(older)).containsExactly("2", "3");
		assertThat(older.hasMore()).isTrue();

		ChatMessagePage oldest = chatMessageService.getMessages(FRIEND, roomId, ids.get(1), null, null, 2);
		assertThat(bodies(oldest)).containsExactly("1");
		assertThat(oldest.hasMore()).isFalse();
	}

	@Test
	void 복구_모드는_첫_응답의_상한을_고정하고_그_안에서만_다음_페이지를_준다() {
		Long first = send(OWNER, "1");
		send(OWNER, "2");
		send(OWNER, "3");
		Long last = send(OWNER, "4");

		ChatMessagePage page1 = chatMessageService.getMessages(FRIEND, roomId, null, first, null, 2);
		assertThat(bodies(page1)).containsExactly("2", "3");
		assertThat(page1.hasMore()).isTrue();
		assertThat(page1.replayThroughMessageId()).isEqualTo(last);

		// 복구 중에 새 메시지가 와도 고정한 상한 밖이라 이번 복구에는 들어오지 않는다(실시간으로 받는다).
		send(FRIEND, "5");
		ChatMessagePage page2 = chatMessageService.getMessages(FRIEND, roomId, null, page1.nextAfterMessageId(),
			page1.replayThroughMessageId(), 2);
		assertThat(bodies(page2)).containsExactly("4");
		assertThat(page2.hasMore()).isFalse();
		assertThat(page2.nextAfterMessageId()).isNull();
		assertThat(page2.replayThroughMessageId()).isEqualTo(last);
	}

	@Test
	void 메시지가_없는_방의_복구는_상한_0_빈_목록() {
		ChatMessagePage page = chatMessageService.getMessages(OWNER, roomId, null, 0L, null, null);

		assertThat(page.items()).isEmpty();
		assertThat(page.hasMore()).isFalse();
		assertThat(page.nextAfterMessageId()).isNull();
		assertThat(page.replayThroughMessageId()).isZero();
	}

	@Test
	void 조회_방향_혼용과_범위_밖_커서는_C003_페이지_크기는_C007() {
		assertError(() -> chatMessageService.getMessages(OWNER, roomId, 5L, 1L, null, null),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatMessageService.getMessages(OWNER, roomId, null, null, 5L, null),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatMessageService.getMessages(OWNER, roomId, null, 5L, 4L, null),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatMessageService.getMessages(OWNER, roomId, 0L, null, null, null),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatMessageService.getMessages(OWNER, roomId, null, -1L, null, null),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatMessageService.getMessages(OWNER, roomId, null, null, null, 51),
			ExceptionType.PAGE_SIZE_OUT_OF_RANGE);
	}

	@Test
	void 지금_최신_ID_보다_큰_afterMessageId_는_C003() {
		Long latest = send(OWNER, "1");

		assertError(() -> chatMessageService.getMessages(OWNER, roomId, null, latest + 10, null, null),
			ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 이력은_현재_참여자만_본다() {
		assertError(() -> chatMessageService.getMessages(STRANGER, roomId, null, null, null, null),
			ExceptionType.ROOM_NOT_MEMBER);
	}

	@Test
	void 탈퇴한_회원의_메시지는_남기고_보낸_사람만_익명화한다() {
		send(FRIEND, "안녕");
		withdraw(FRIEND);

		ChatMessage message = chatMessageService.getMessages(OWNER, roomId, null, null, null, null).items().getFirst();
		assertThat(message.sender()).isEqualTo(new ChatMessage.Sender(null, "탈퇴한 사용자"));
		assertThat(message.clientMessageId()).isNull();
		assertThat(message.body()).isEqualTo("안녕");
	}

	/* ── 읽음(API-088)과 안 읽음 점(API-020·022·076) ─────────────── */

	@Test
	void 안_읽음_점은_읽은_위치_뒤의_타인_메시지로만_정한다() {
		Long id = send(OWNER, "1");

		// 내가 보낸 메시지는 안 읽음이 아니다. 보냈다고 읽은 위치가 바뀌지도 않는다.
		assertThat(chatMessageService.getMessages(OWNER, roomId, null, null, null, null).chatReadState())
			.satisfies(state -> {
				assertThat(state.lastReadMessageId()).isNull();
				assertThat(state.hasUnreadMessages()).isFalse();
			});
		assertThat(chatMessageService.getMessages(FRIEND, roomId, null, null, null, null).chatReadState()
			.hasUnreadMessages()).isTrue();
		assertThat(roomService.getRoom(FRIEND, roomId).chatReadState().hasUnreadMessages()).isTrue();
		assertThat(roomService.getMyRooms(FRIEND, null, null).items().getFirst().chatReadState().hasUnreadMessages())
			.isTrue();

		ChatReadResponse read = chatReadService.markRead(FRIEND, roomId, id);
		assertThat(read).isEqualTo(new ChatReadResponse(roomId, id, false));
		assertThat(roomService.getRoom(FRIEND, roomId).chatReadState().hasUnreadMessages()).isFalse();
		assertThat(roomService.getMyRooms(FRIEND, null, null).items().getFirst().chatReadState().hasUnreadMessages())
			.isFalse();
	}

	@Test
	void 읽은_위치는_뒤로_가지_않는다() {
		Long first = send(OWNER, "1");
		Long second = send(OWNER, "2");
		chatReadService.markRead(FRIEND, roomId, second);

		ChatReadResponse response = chatReadService.markRead(FRIEND, roomId, first);

		assertThat(response.lastReadMessageId()).isEqualTo(second);
		em.flush();
		em.clear();
		assertThat(em.find(RoomMember.class, new RoomMemberId(roomId, FRIEND)).getLastReadMessageId())
			.isEqualTo(second);
	}

	@Test
	void 읽은_위치_뒤에_타인_메시지가_더_있으면_true() {
		Long first = send(OWNER, "1");
		send(OWNER, "2");

		assertThat(chatReadService.markRead(FRIEND, roomId, first).hasUnreadMessages()).isTrue();
	}

	@Test
	void 양수가_아니거나_다른_방_메시지면_C003_참여자가_아니면_RO002() {
		Long otherRoom = createRoom(STRANGER);
		Long otherMessage = chatMessageService.send(STRANGER, otherRoom, "cm-x", "다른 방").message().id();

		assertError(() -> chatReadService.markRead(FRIEND, roomId, 0), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatReadService.markRead(FRIEND, roomId, otherMessage), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatReadService.markRead(FRIEND, roomId, 999_999), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> chatReadService.markRead(STRANGER, roomId, otherMessage), ExceptionType.ROOM_NOT_MEMBER);
	}
}
