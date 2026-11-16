package com.ssafy.yeogirok.domain.user.cleanup;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomDeletedEvent;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.room.service.WithdrawnMemberRooms;
import com.ssafy.yeogirok.domain.statistics.WithdrawnFootprints;
import com.ssafy.yeogirok.domain.statistics.entity.FootprintStatistic;
import com.ssafy.yeogirok.support.TestUsers;
import com.ssafy.yeogirok.support.WithdrawalJpaTestConfig;

/** 탈퇴 데이터 정리가 생기기 전에 탈퇴한 회원의 발자취·빈 방을 마저 정리한다. */
@DataJpaTest
@Import({WithdrawalCleanupJob.class, WithdrawnFootprints.class, WithdrawnMemberRooms.class, RoomTestConfig.class,
	WithdrawalJpaTestConfig.class})
@TestPropertySource(locations = "classpath:withdrawal-test.properties")
@RecordApplicationEvents
class WithdrawalCleanupJobTest {

	static final Long WITHDRAWN = 3101L;
	static final Long ACTIVE = 3102L;
	static final Long ROOM_ONLY_WITHDRAWN = 3103L;

	@Autowired
	WithdrawalCleanupJob job;
	@Autowired
	RoomService roomService;
	@Autowired
	TestEntityManager em;
	@Autowired
	ApplicationEvents events;

	@BeforeEach
	void setUp() {
		TestUsers.insertActive(em.getEntityManager(), ACTIVE, ROOM_ONLY_WITHDRAWN);
		TestUsers.insert(em.getEntityManager(), WITHDRAWN, "WITHDRAWN");
	}

	@Test
	void 발자취가_남은_탈퇴자만_보존분으로_옮기고_원본을_지운다() {
		em.persist(footprintOf(WITHDRAWN).build());
		em.persist(footprintOf(WITHDRAWN).build());
		em.persist(footprintOf(ACTIVE).build());
		em.flush();

		job.cleanUp();
		em.flush();
		em.clear();

		assertThat(footprintCount(WITHDRAWN)).isZero();
		assertThat(footprintCount(ACTIVE)).isEqualTo(1);
		assertThat(em.getEntityManager().createQuery("select s from FootprintStatistic s", FootprintStatistic.class)
			.getResultList()).singleElement().extracting(FootprintStatistic::getVisitCount).isEqualTo(2L);
	}

	@Test
	void 참여자가_모두_탈퇴한_방만_지운다() {
		Long emptyRoom = roomService.create(ROOM_ONLY_WITHDRAWN,
			new RoomCreateCommand("빈 방", null, null, TransportMode.CAR)).id();
		Long liveRoom = roomService.create(ACTIVE, new RoomCreateCommand("남은 방", null, null, TransportMode.CAR)).id();
		// 참여자는 모두 탈퇴했지만 공개 코스가 있는 방. 지우면 코스가 내려가므로 남긴다.
		Long courseRoom = roomService.create(ROOM_ONLY_WITHDRAWN,
			new RoomCreateCommand("코스 방", null, null, TransportMode.CAR)).id();
		TravelRecord course = TravelRecord.create(courseRoom, "코스", RoomTestConfig.NOW);
		course.changeCourse("코스", "", RoomTestConfig.NOW, false, RoomTestConfig.NOW);
		em.persist(course);
		// 이 정리가 생기기 전에 탈퇴해 방이 남은 상태.
		em.getEntityManager().createNativeQuery("UPDATE users SET status = 'WITHDRAWN' WHERE id = :id")
			.setParameter("id", ROOM_ONLY_WITHDRAWN)
			.executeUpdate();
		events.clear();

		job.cleanUp();
		em.flush();
		em.clear();

		assertThat(em.find(TravelRoom.class, emptyRoom).isDeleted()).isTrue();
		assertThat(em.find(TravelRoom.class, liveRoom).isDeleted()).isFalse();
		assertThat(em.find(TravelRoom.class, courseRoom).isDeleted()).isFalse();
		assertThat(events.stream(RoomDeletedEvent.class)).containsExactly(new RoomDeletedEvent(emptyRoom));
	}

	@Test
	void 정리할_게_없으면_아무것도_바꾸지_않는다() {
		roomService.create(ACTIVE, new RoomCreateCommand("남은 방", null, null, TransportMode.CAR));
		events.clear();

		job.cleanUp();

		assertThat(events.stream(RoomDeletedEvent.class)).isEmpty();
	}

	private long footprintCount(Long userId) {
		List<Long> ids = em.getEntityManager()
			.createQuery("select f.id from Footprint f where f.userId = :userId", Long.class)
			.setParameter("userId", userId)
			.getResultList();
		return ids.size();
	}
}
