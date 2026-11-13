package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.dto.PlannedSchedule;
import com.ssafy.yeogirok.domain.record.dto.RecordCreateResponse;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
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
	ReferencePlans referencePlans;
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

	/* ── 참고 계획 사본(노션 057, 명세 specs/record-plan.md 3-1) ───────────── */

	static final LocalDate DAY1 = YESTERDAY.minusDays(1);
	static final Instant ARRIVAL = Instant.parse("2026-11-07T01:00:00Z");

	private RoomPlace roomPlace(Long roomId, String name) {
		Place place = em.persist(Place.manual(name, new BigDecimal("37.7950000"), new BigDecimal("128.8960000"),
			"강원 강릉시", null, null, NOW));
		return em.persist(RoomPlace.manual(roomId, place, MEMBER, NOW));
	}

	private ScheduleItem item(Long roomId, RoomPlace roomPlace, LocalDate date, int position) {
		return em.persist(ScheduleItem.create(roomId, roomPlace.getId(), date, position, 60, false));
	}

	@Test
	void 확정_일정이_있으면_방_version_날짜_순서_장소를_사본으로_저장한다() {
		Long roomId = room(YESTERDAY);
		RoomPlace cafe = roomPlace(roomId, "안목 카페");
		RoomPlace beach = roomPlace(roomId, "경포 해변");
		RoomPlace unassigned = roomPlace(roomId, "넣지 못한 후보");
		ScheduleItem first = item(roomId, cafe, DAY1, 1);
		first.applyTiming(ARRIVAL, ARRIVAL.plusSeconds(3600), null);
		ScheduleItem second = item(roomId, beach, DAY1, 2);
		second.applyTiming(null, null, TravelLeg.found(15, new BigDecimal("5000"), "KAKAO", NOW));
		ScheduleItem third = item(roomId, cafe, YESTERDAY, 1);
		TravelRoom room = em.find(TravelRoom.class, roomId);
		room.updateScheduleOptions(new ScheduleOptions(
			List.of(new ScheduleOptions.DayOption(DAY1, LocalTime.of(9, 0), LocalTime.of(21, 0))), null, null, null,
			List.of(new ScheduleOptions.UnassignedPlace(unassigned.getId()))).toMap(), NOW);
		em.flush();
		int roomVersion = room.getVersion();

		Long recordId = recordCreateService.create(MEMBER, roomId, null).recordId();
		em.flush();
		em.clear();

		TravelRecord saved = em.find(TravelRecord.class, recordId);
		assertThat(saved.getReferencePlan()).doesNotContainKeys("unassignedPlaces", "startPoint", "endPoint");
		PlannedSchedule plan = referencePlans.read(saved);
		assertThat(plan.roomId()).isEqualTo(roomId);
		assertThat(plan.version()).isEqualTo(roomVersion);
		assertThat(plan.days()).extracting(PlannedSchedule.Day::date).containsExactly(DAY1, YESTERDAY);
		PlannedSchedule.Day day1 = plan.days().getFirst();
		assertThat(day1.startTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(day1.items()).extracting(PlannedSchedule.Item::id, PlannedSchedule.Item::order)
			.containsExactly(tuple(first.getId(), 1),
				tuple(second.getId(), 2));
		assertThat(day1.items().getFirst().arrivalAt()).isEqualTo(ARRIVAL);
		assertThat(day1.items().getFirst().place().name()).isEqualTo("안목 카페");
		assertThat(day1.items().getFirst().place().latitude()).isEqualByComparingTo("37.795");
		assertThat(day1.legs()).singleElement()
			.satisfies(leg -> {
				assertThat(leg.fromItemId()).isEqualTo(first.getId());
				assertThat(leg.transportMode()).isEqualTo(TransportMode.CAR);
				assertThat(leg.travelMinutes()).isEqualTo(15);
			});
		assertThat(plan.days().get(1).items()).extracting(PlannedSchedule.Item::id).containsExactly(third.getId());
	}

	@Test
	void 후보에서_뺀_장소도_확정_일정에_있으면_사본에_넣는다() {
		Long roomId = room(YESTERDAY);
		RoomPlace removed = roomPlace(roomId, "뺀 후보");
		ScheduleItem item = item(roomId, removed, YESTERDAY, 1);
		removed.remove(NOW);
		em.flush();

		Long recordId = recordCreateService.create(MEMBER, roomId, null).recordId();

		PlannedSchedule plan = referencePlans.read(em.find(TravelRecord.class, recordId));
		assertThat(plan.days().getFirst().items()).singleElement().satisfies(planned -> {
			assertThat(planned.id()).isEqualTo(item.getId());
			assertThat(planned.place().name()).isEqualTo("뺀 후보");
		});
	}

	@Test
	void 만든_뒤_방_일정이_바뀌어도_사본은_그대로다() {
		Long roomId = room(YESTERDAY);
		RoomPlace cafe = roomPlace(roomId, "안목 카페");
		ScheduleItem item = item(roomId, cafe, YESTERDAY, 1);
		em.flush();
		Long recordId = recordCreateService.create(MEMBER, roomId, null).recordId();

		item.place(cafe.getId(), DAY1, 1, 120, true);
		item(roomId, roomPlace(roomId, "새 장소"), YESTERDAY, 2);
		em.flush();
		em.clear();

		PlannedSchedule plan = referencePlans.read(em.find(TravelRecord.class, recordId));
		assertThat(plan.days()).singleElement().satisfies(day -> {
			assertThat(day.date()).isEqualTo(YESTERDAY);
			assertThat(day.items()).singleElement().satisfies(planned -> {
				assertThat(planned.stayMinutes()).isEqualTo(60);
				assertThat(planned.fixed()).isFalse();
			});
		});
	}

	@Test
	void 다른_방의_일정은_섞이지_않는다() {
		Long roomId = room(YESTERDAY);
		Long otherRoomId = room(YESTERDAY);
		item(otherRoomId, roomPlace(otherRoomId, "다른 방 장소"), YESTERDAY, 1);
		em.flush();

		Long recordId = recordCreateService.create(MEMBER, roomId, null).recordId();

		assertThat(em.find(TravelRecord.class, recordId).getReferencePlan()).isNull();
	}
}
