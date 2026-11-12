package com.ssafy.yeogirok.domain.recommendation.service;

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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ssafy.yeogirok.domain.recommendation.RecommendationTestConfig;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceSaveCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomChange;
import com.ssafy.yeogirok.domain.room.event.RoomChangedEvent;
import com.ssafy.yeogirok.domain.room.service.RoomInvitationService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

@DataJpaTest
@Import(RecommendationTestConfig.class)
@RecordApplicationEvents
class RecommendationPreferenceServiceTest {

	static final Long OWNER = 7001L;
	static final Long FRIEND = 7002L;
	static final Long STRANGER = 7003L;

	@Autowired
	RecommendationPreferenceService preferenceService;
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

	Long roomId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		TestUsers.insertActive(em.getEntityManager(), OWNER, FRIEND, STRANGER);
		roomId = roomService.create(OWNER, new RoomCreateCommand("강릉 여행", null, null, TransportMode.CAR)).id();
		roomInvitationService.accept(FRIEND,
			roomInvitationService.getOrCreateInvitation(OWNER, roomId).inviteToken());
	}

	private int roomVersion() {
		em.flush();
		em.clear();
		return em.find(TravelRoom.class, roomId).getVersion();
	}

	@Test
	void 저장한_적_없으면_빈_조건과_version_1() {
		PreferenceResponse response = preferenceService.get(OWNER, roomId);

		assertThat(response.roomId()).isEqualTo(roomId);
		assertThat(response.preferences()).isEqualTo(new PreferenceResponse.Values(null, List.of(), false));
		assertThat(response.version()).isEqualTo(1);
	}

	@Test
	void 저장하면_조건_version_이_오르고_여행방_version_은_그대로다() {
		int before = roomVersion();

		PreferenceResponse saved = preferenceService.save(OWNER, roomId,
			new PreferenceSaveCommand(1, "51150", List.of("CAFE", "NATURE")));

		assertThat(saved.version()).isEqualTo(2);
		assertThat(saved.preferences()).isEqualTo(new PreferenceResponse.Values("51150", List.of("CAFE", "NATURE"), false));
		assertThat(roomVersion()).isEqualTo(before);

		// 다른 참여자가 조회하고 다음 version 으로 다시 저장한다.
		assertThat(preferenceService.get(FRIEND, roomId)).isEqualTo(saved);
		assertThat(preferenceService.save(FRIEND, roomId, new PreferenceSaveCommand(2, "51210", List.of("FOOD")))
			.version()).isEqualTo(3);
	}

	@Test
	void 낡은_version_이면_C004() {
		preferenceService.save(OWNER, roomId, new PreferenceSaveCommand(1, "51150", List.of("CAFE")));

		assertThatThrownBy(() -> preferenceService.save(FRIEND, roomId,
			new PreferenceSaveCommand(1, "51210", List.of("FOOD"))))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.VERSION_MISMATCH));
	}

	@Test
	void 참여자가_아니면_RO002() {
		assertThatThrownBy(() -> preferenceService.get(STRANGER, roomId))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.ROOM_NOT_MEMBER));
		assertThatThrownBy(() -> preferenceService.save(STRANGER, roomId,
			new PreferenceSaveCommand(1, "51150", List.of("CAFE"))))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.ROOM_NOT_MEMBER));
	}

	/* ── 실시간 알림(ROOM_UPDATED) 이벤트 ─────────────────────── */

	@Test
	void 조건을_저장하면_PREFERENCES_변경을_알리고_실패하면_알리지_않는다() {
		int roomVersion = roomVersion();
		events.clear();

		preferenceService.save(FRIEND, roomId, new PreferenceSaveCommand(1, "51150", List.of("CAFE")));
		assertThatThrownBy(() -> preferenceService.save(OWNER, roomId,
			new PreferenceSaveCommand(1, "51210", List.of("FOOD")))).isInstanceOf(BusinessException.class);

		// 여행방 version 은 오르지 않으므로 지금 값 그대로 싣는다.
		assertThat(events.stream(RoomChangedEvent.class))
			.containsExactly(RoomChangedEvent.of(roomId, FRIEND, roomVersion, RoomChange.PREFERENCES));
	}
}
