package com.ssafy.yeogirok.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 조회는 RE001·RE002, 쓰기는 RE001·RE003. */
@DataJpaTest
@Import(RecordFinder.class)
class RecordFinderTest {

	static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	static final Long MEMBER = 3001L;
	static final Long LEFT = 3002L;
	static final Long STRANGER = 3003L;

	@Autowired
	RecordFinder recordFinder;
	@Autowired
	TestEntityManager em;

	TravelRoom room;
	Long recordId;

	@BeforeEach
	void setUp() {
		room = em.persist(TravelRoom.builder().title("가을 여행").transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), MEMBER, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW.plusSeconds(60));
		recordId = em.persist(TravelRecord.create(room.getId(), "가을 여행", NOW)).getId();
		em.flush();
		em.clear();
	}

	@Test
	void 현재_참여자는_조회와_쓰기를_할_수_있다() {
		assertThat(recordFinder.getReadable(recordId, MEMBER).room().getId()).isEqualTo(room.getId());
		assertThat(recordFinder.lockWritable(recordId, MEMBER).record().getId()).isEqualTo(recordId);
	}

	@Test
	void 나간_사람과_참여한_적_없는_사람은_조회_RE002_쓰기_RE003() {
		for (Long user : new Long[] {LEFT, STRANGER}) {
			assertError(() -> recordFinder.getReadable(recordId, user), ExceptionType.RECORD_NOT_MEMBER);
			assertError(() -> recordFinder.lockWritable(recordId, user), ExceptionType.RECORD_EDIT_FORBIDDEN);
		}
	}

	@Test
	void 없는_기록은_조회_쓰기_모두_RE001() {
		assertError(() -> recordFinder.getReadable(recordId + 100, MEMBER), ExceptionType.RECORD_NOT_FOUND);
		assertError(() -> recordFinder.lockWritable(recordId + 100, MEMBER), ExceptionType.RECORD_NOT_FOUND);
	}

	@Test
	void 방이_삭제된_기록은_조회_RE001_쓰기_RE003() {
		em.find(TravelRoom.class, room.getId()).softDelete(NOW.plusSeconds(120));
		em.flush();
		em.clear();

		assertError(() -> recordFinder.getReadable(recordId, MEMBER), ExceptionType.RECORD_NOT_FOUND);
		assertError(() -> recordFinder.lockWritable(recordId, MEMBER), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}
}
