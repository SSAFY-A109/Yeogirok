package com.ssafy.yeogirok.domain.room.service;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomDeletedEvent;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

/** 탈퇴 정리 중 여행방. 탈퇴 트랜잭션처럼 회원을 WITHDRAWN 으로 바꾼 뒤 같은 트랜잭션에서 리스너를 부른다. */
@DataJpaTest
@Import({RoomTestConfig.class, WithdrawnMemberRooms.class})
@RecordApplicationEvents
class WithdrawnMemberRoomsTest {

	static final Long OWNER = 2101L;
	static final Long FRIEND = 2102L;
	static final Long THIRD = 2103L;
	static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

	@Autowired
	WithdrawnMemberRooms withdrawnMemberRooms;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;
	@Autowired
	ApplicationEvents events;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		TestUsers.insertActive(em.getEntityManager(), OWNER, FRIEND, THIRD);
	}

	@Test
	void 혼자_남아_있던_방만_삭제하고_다른_참여자가_있는_방은_그대로_둔다() {
		Long alone = createRoom(FRIEND);
		Long shared = createRoom(OWNER);
		join(FRIEND, shared);
		events.clear();

		withdraw(FRIEND);

		assertThat(em.find(TravelRoom.class, alone).isDeleted()).isTrue();
		assertThat(em.find(TravelRoom.class, shared).isDeleted()).isFalse();
		assertThat(events.stream(RoomDeletedEvent.class)).containsExactly(new RoomDeletedEvent(alone));
		// 참여 행은 남긴다. 참여자 목록·인원에서는 계정 상태로 빠진다.
		assertThat(em.find(RoomMember.class, new RoomMemberId(shared, FRIEND)).isCurrent()).isTrue();
		assertThat(roomService.getRoom(OWNER, shared).members()).extracting("userId").containsExactly(OWNER);
	}

	@Test
	void 공개_코스가_있는_방은_참여자가_0명이_돼도_남긴다() {
		Long published = createRoom(FRIEND);
		Long unpublished = createRoom(FRIEND);
		saveRecord(published, true);
		saveRecord(unpublished, false);
		events.clear();

		withdraw(FRIEND);

		// 공개 코스는 원본 방이 삭제되면 내려가므로 방을 남긴다. 기록만 있고 게시하지 않은 방은 지운다.
		assertThat(em.find(TravelRoom.class, published).isDeleted()).isFalse();
		assertThat(em.find(TravelRoom.class, unpublished).isDeleted()).isTrue();
		assertThat(events.stream(RoomDeletedEvent.class)).containsExactly(new RoomDeletedEvent(unpublished));
	}

	@Test
	void 남은_참여자가_모두_탈퇴한_회원이면_마지막_탈퇴에서_방을_삭제한다() {
		Long roomId = createRoom(OWNER);
		join(FRIEND, roomId);

		withdraw(FRIEND);
		assertThat(em.find(TravelRoom.class, roomId).isDeleted()).isFalse();

		withdraw(OWNER);
		assertThat(em.find(TravelRoom.class, roomId).isDeleted()).isTrue();
		assertThat(events.stream(RoomDeletedEvent.class)).containsExactly(new RoomDeletedEvent(roomId));
	}

	@Test
	void 이미_나간_방과_삭제된_방은_건드리지_않는다() {
		Long left = createRoom(OWNER);
		join(FRIEND, left);
		roomService.leave(FRIEND, left);
		Long deleted = createRoom(THIRD);
		join(FRIEND, deleted);
		roomService.leave(THIRD, deleted);
		roomService.leave(FRIEND, deleted);
		events.clear();

		withdraw(FRIEND);

		assertThat(em.find(TravelRoom.class, left).isDeleted()).isFalse();
		assertThat(events.stream(RoomDeletedEvent.class)).isEmpty();
	}

	private void withdraw(Long userId) {
		em.getEntityManager().createNativeQuery("UPDATE users SET status = 'WITHDRAWN' WHERE id = :id")
			.setParameter("id", userId)
			.executeUpdate();
		withdrawnMemberRooms.onUserWithdrawn(new UserWithdrawnEvent(userId, OAuthProvider.KAKAO, "kakao-" + userId, null));
		em.flush();
		em.clear();
	}

	private void saveRecord(Long roomId, boolean published) {
		TravelRecord record = TravelRecord.create(roomId, "가을 여행 기록", NOW);
		if (published) {
			record.changeCourse("가을 여행 코스", "", NOW, false, NOW);
		}
		em.persist(record);
		em.flush();
	}

	private Long createRoom(Long userId) {
		return roomService.create(userId, new RoomCreateCommand("가을 여행", TODAY, TODAY.plusDays(1),
			TransportMode.CAR)).id();
	}

	private void join(Long userId, Long roomId) {
		String token = roomInvitationService.getOrCreateInvitation(ownerOf(roomId), roomId).inviteToken();
		roomInvitationService.accept(userId, token);
	}

	private Long ownerOf(Long roomId) {
		return em.getEntityManager()
			.createQuery("select m.userId from RoomMember m where m.roomId = :roomId and m.creator = true", Long.class)
			.setParameter("roomId", roomId)
			.getSingleResult();
	}
}
