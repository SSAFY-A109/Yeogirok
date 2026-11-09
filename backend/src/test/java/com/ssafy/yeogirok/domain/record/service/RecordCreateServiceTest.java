package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.RecordCreateResponse;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;

@DataJpaTest
@Import(RecordTestConfig.class)
class RecordCreateServiceTest {

	static final Long MEMBER = 4001L;
	static final Long LEFT = 4002L;
	static final Long STRANGER = 4003L;
	/** 서울 기준 어제(NOW 는 11/9 00:30 KST). */
	static final LocalDate YESTERDAY = LocalDate.parse("2026-11-08");

	@Autowired
	RecordCreateService recordCreateService;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
	}

	private Long room(LocalDate endDate) {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행")
			.startDate(endDate == null ? null : endDate.minusDays(1)).endDate(endDate)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), MEMBER, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		em.flush();
		return room.getId();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 서울_기준_종료일이_어제면_DRAFT_기록을_만들고_제목을_생략하면_방_제목을_쓴다() {
		Long roomId = room(YESTERDAY);

		RecordCreateResponse response = recordCreateService.create(MEMBER, roomId, null);

		assertThat(response.roomId()).isEqualTo(roomId);
		assertThat(response.status()).isEqualTo(RecordStatus.DRAFT);
		assertThat(response.version()).isEqualTo(1);
		TravelRecord saved = em.find(TravelRecord.class, response.recordId());
		assertThat(saved.getTitle()).isEqualTo("강릉 여행");
		assertThat(saved.getReferencePlan()).isNull();
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void 제목을_보내면_그_제목으로_만든다() {
		Long recordId = recordCreateService.create(MEMBER, room(YESTERDAY), "가을 강릉").recordId();

		assertThat(em.find(TravelRecord.class, recordId).getTitle()).isEqualTo("가을 강릉");
	}

	@ParameterizedTest
	@ValueSource(strings = {"2026-11-09", "2026-11-10"})
	void 종료일이_서울_기준_오늘이거나_미래면_RE024(String endDate) {
		Long roomId = room(LocalDate.parse(endDate));

		assertError(() -> recordCreateService.create(MEMBER, roomId, null), ExceptionType.TRIP_NOT_ENDED);
	}

	@Test
	void 날짜_미정이면_RE024() {
		Long roomId = room(null);

		assertError(() -> recordCreateService.create(MEMBER, roomId, null), ExceptionType.TRIP_NOT_ENDED);
	}

	@Test
	void 방에_기록이_이미_있으면_RE017() {
		Long roomId = room(YESTERDAY);
		recordCreateService.create(MEMBER, roomId, null);

		assertError(() -> recordCreateService.create(MEMBER, roomId, null), ExceptionType.RECORD_ALREADY_EXISTS);
	}

	@Test
	void 없는_방_RO001_나간_사람과_비참여자_RO002() {
		Long roomId = room(YESTERDAY);

		assertError(() -> recordCreateService.create(MEMBER, roomId + 100, null), ExceptionType.ROOM_NOT_FOUND);
		assertError(() -> recordCreateService.create(LEFT, roomId, null), ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> recordCreateService.create(STRANGER, roomId, null), ExceptionType.ROOM_NOT_MEMBER);
	}

	@Test
	void 멱등_재전송은_종료일을_다시_보지_않고_현재_참여만_확인한다() {
		Long roomId = room(YESTERDAY);
		Long recordId = recordCreateService.create(MEMBER, roomId, null).recordId();
		em.find(TravelRoom.class, roomId).updateInfo("강릉 여행", null, null, TransportMode.CAR, NOW);
		em.flush();

		assertThat(recordCreateService.getCreated(MEMBER, roomId, recordId).recordId()).isEqualTo(recordId);
		assertError(() -> recordCreateService.getCreated(LEFT, roomId, recordId), ExceptionType.ROOM_NOT_MEMBER);
	}
}
