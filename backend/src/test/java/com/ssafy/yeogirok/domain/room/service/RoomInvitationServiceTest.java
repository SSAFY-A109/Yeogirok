package com.ssafy.yeogirok.domain.room.service;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomInvitationResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomJoinResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

@DataJpaTest
@Import(RoomTestConfig.class)
class RoomInvitationServiceTest {

	static final Long OWNER = 3001L;
	static final Long FRIEND = 3002L;
	static final Long STRANGER = 3003L;
	/** 정원 테스트용 회원 3101~3111. */
	static final long CROWD_START = 3101L;

	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	RoomService roomService;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	Long roomId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		TestUsers.insertActive(em.getEntityManager(), OWNER, FRIEND, STRANGER);
		TestUsers.insertActive(em.getEntityManager(), LongStream.range(CROWD_START, CROWD_START + 11).toArray());
		roomId = roomService.create(OWNER, new RoomCreateCommand("가을 여행", null, null, TransportMode.CAR)).id();
	}

	private String inviteToken() {
		return roomInvitationService.getOrCreateInvitation(OWNER, roomId).inviteToken();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	/* ── API-024 초대 링크 ──────────────────────────────────── */

	@Test
	void 처음_요청하면_링크를_만들고_다시_요청하면_같은_링크를_준다() {
		RoomInvitationResponse first = roomInvitationService.getOrCreateInvitation(OWNER, roomId);
		RoomInvitationResponse again = roomInvitationService.getOrCreateInvitation(OWNER, roomId);

		assertThat(first.created()).isTrue();
		assertThat(again.created()).isFalse();
		assertThat(again.inviteToken()).isEqualTo(first.inviteToken());
		assertThat(first.invitationId()).isEqualTo(roomId);
		assertThat(first.expiresAt()).isNull();
		assertThat(first.inviteUrl()).isEqualTo("https://test.yeogirok/invite/" + first.inviteToken());
	}

	@Test
	void DB에는_토큰_원문이_아니라_암호문이_저장된다() {
		String token = inviteToken();
		em.clear();

		String stored = em.find(TravelRoom.class, roomId).getInviteTokenEncrypted();
		assertThat(stored).isNotNull().doesNotContain(token);
	}

	@Test
	void 다른_참여자가_요청해도_같은_링크다() {
		String token = inviteToken();
		roomInvitationService.accept(FRIEND, token);

		assertThat(roomInvitationService.getOrCreateInvitation(FRIEND, roomId).inviteToken()).isEqualTo(token);
	}

	@Test
	void 참여자가_아니면_RO002_없는_방이면_RO001() {
		assertError(() -> roomInvitationService.getOrCreateInvitation(STRANGER, roomId), ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> roomInvitationService.getOrCreateInvitation(OWNER, 999_999L), ExceptionType.ROOM_NOT_FOUND);
	}

	/* ── API-025 참여 ───────────────────────────────────────── */

	@Test
	void 링크로_바로_참여한다() {
		RoomJoinResponse response = roomInvitationService.accept(FRIEND, inviteToken());
		em.clear();

		assertThat(response).isEqualTo(RoomJoinResponse.joined(roomId, false));
		RoomMember member = em.find(RoomMember.class, new RoomMemberId(roomId, FRIEND));
		assertThat(member.isCurrent()).isTrue();
		assertThat(member.isCreator()).isFalse();
		assertThat(member.getLastReadMessageId()).isNull();
	}

	@Test
	void 이미_참여_중이면_그대로_성공한다() {
		String token = inviteToken();
		roomInvitationService.accept(FRIEND, token);

		assertThat(roomInvitationService.accept(FRIEND, token)).isEqualTo(RoomJoinResponse.joined(roomId, false));
		assertThat(roomInvitationService.accept(OWNER, token)).isEqualTo(RoomJoinResponse.joined(roomId, true));
	}

	@Test
	void 나갔다가_다시_들어오면_같은_행을_되살리고_생성자_표시를_유지한다() {
		String token = inviteToken();
		roomInvitationService.accept(FRIEND, token);
		roomService.leave(OWNER, roomId);
		clock.advance(Duration.ofHours(1));

		RoomJoinResponse response = roomInvitationService.accept(OWNER, token);
		em.clear();

		assertThat(response.isCreator()).isTrue();
		RoomMember member = em.find(RoomMember.class, new RoomMemberId(roomId, OWNER));
		assertThat(member.isCurrent()).isTrue();
		assertThat(member.getJoinedAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
	}

	@Test
	void 현재_10명이면_새_참여는_RO007_이미_참여_중인_사람은_성공() {
		String token = inviteToken();
		for (long id = CROWD_START; id < CROWD_START + 9; id++) {
			roomInvitationService.accept(id, token);
		}

		assertError(() -> roomInvitationService.accept(CROWD_START + 9, token), ExceptionType.ROOM_FULL);
		assertThat(roomInvitationService.accept(CROWD_START, token).roomId()).isEqualTo(roomId);
		// 링크 조회는 만원이어도 된다.
		assertThat(roomInvitationService.getOrCreateInvitation(OWNER, roomId).inviteToken()).isEqualTo(token);
	}

	@Test
	void 퇴장자는_인원에서_빠져_자리가_생기면_참여할_수_있다() {
		String token = inviteToken();
		for (long id = CROWD_START; id < CROWD_START + 9; id++) {
			roomInvitationService.accept(id, token);
		}
		roomService.leave(CROWD_START, roomId);

		assertThat(roomInvitationService.accept(CROWD_START + 9, token).roomId()).isEqualTo(roomId);
		assertError(() -> roomInvitationService.accept(CROWD_START, token), ExceptionType.ROOM_FULL);
	}

	@Test
	void 모르는_토큰은_RO005() {
		inviteToken();

		assertError(() -> roomInvitationService.accept(FRIEND, "unknown-token"),
			ExceptionType.ROOM_INVITE_TOKEN_INVALID);
		assertError(() -> roomInvitationService.accept(FRIEND, "x".repeat(200)),
			ExceptionType.ROOM_INVITE_TOKEN_INVALID);
		assertError(() -> roomInvitationService.accept(FRIEND, ""), ExceptionType.ROOM_INVITE_TOKEN_INVALID);
	}

	@Test
	void 삭제된_방의_링크로는_참여할_수_없고_RO001() {
		String token = inviteToken();
		roomService.leave(OWNER, roomId);

		assertError(() -> roomInvitationService.accept(FRIEND, token), ExceptionType.ROOM_NOT_FOUND);
		assertError(() -> roomInvitationService.accept(OWNER, token), ExceptionType.ROOM_NOT_FOUND);
	}

	@Test
	void 참여_재조회는_현재_참여자에게만_준다() {
		roomInvitationService.accept(FRIEND, inviteToken());

		assertThat(roomInvitationService.getJoined(FRIEND, roomId)).isEqualTo(RoomJoinResponse.joined(roomId, false));
		assertError(() -> roomInvitationService.getJoined(STRANGER, roomId), ExceptionType.ROOM_NOT_MEMBER);
	}
}
