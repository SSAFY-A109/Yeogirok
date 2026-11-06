package com.ssafy.yeogirok.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.support.TestUsers;

/** 기록 참여자 = 기록이 있고, 그 방이 삭제되지 않았고, 현재 참여 중인 회원. */
@DataJpaTest
@Import({RecordAccess.class, RoomFinder.class})
class RecordAccessTest {

	static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
	static final Long MEMBER = 3001L;
	static final Long LEFT = 3002L;
	static final Long STRANGER = 3003L;

	@Autowired
	RecordAccess recordAccess;
	@Autowired
	TravelRecordRepository travelRecordRepository;
	@Autowired
	TestEntityManager em;

	TravelRoom room;
	Long recordId;

	@BeforeEach
	void setUp() {
		TestUsers.insertActive(em.getEntityManager(), MEMBER, LEFT, STRANGER);
		room = em.persist(TravelRoom.builder().title("가을 여행").transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), MEMBER, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW.plusSeconds(60));
		recordId = travelRecordRepository.save(TravelRecord.create(room.getId(), "가을 여행", NOW)).getId();
		em.flush();
	}

	@Test
	void 현재_참여자만_기록_참여자다() {
		assertThat(recordAccess.isCurrentMember(recordId, MEMBER)).isTrue();
		assertThat(recordAccess.isCurrentMember(recordId, LEFT)).isFalse();
		assertThat(recordAccess.isCurrentMember(recordId, STRANGER)).isFalse();
	}

	@Test
	void 기록이_없으면_참여자가_아니다() {
		assertThat(recordAccess.isCurrentMember(recordId + 1000, MEMBER)).isFalse();
	}

	@Test
	void 방이_삭제되면_참여자가_아니다() {
		room.softDelete(NOW.plusSeconds(120));
		em.flush();
		em.clear();

		assertThat(recordAccess.isCurrentMember(recordId, MEMBER)).isFalse();
	}

	@Test
	void 기록_ID_로_방_ID_를_찾는다() {
		assertThat(travelRecordRepository.findRoomIdById(recordId)).contains(room.getId());
	}
}
