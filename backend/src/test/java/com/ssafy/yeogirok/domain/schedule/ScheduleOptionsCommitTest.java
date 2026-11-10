package com.ssafy.yeogirok.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.UnassignedPlace;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.support.TestUsers;

import jakarta.persistence.EntityManager;

/**
 * 일정 옵션(JSON)을 바꿔 실제로 커밋하면 방 version 이 한 번만 오르는지. 맵에 JSON 을 다시 읽은 것과 다른 타입(예: Long)이
 * 있으면 flush 뒤 커밋 때 또 바뀐 것으로 보여 version 이 두 번 오른다. 테스트 트랜잭션은 커밋하지 않아 따로 본다.
 */
@DataJpaTest
@Import(RoomTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleOptionsCommitTest {

	static final Long OWNER = 8800L;

	@Autowired
	RoomService roomService;
	@Autowired
	EntityManager entityManager;
	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	DataSource dataSource;

	Long roomId;

	@AfterEach
	void tearDown() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("DELETE FROM room_members WHERE room_id = ?", roomId);
		jdbc.update("DELETE FROM travel_rooms WHERE id = ?", roomId);
		jdbc.update("DELETE FROM users WHERE id = ?", OWNER);
	}

	@Test
	void 일정_옵션을_모두_채워_커밋해도_version은_한_번만_오른다() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		TestUsers.insertActive(jdbc, OWNER);
		LocalDate day = LocalDate.of(2026, 11, 5);
		roomId = roomService.create(OWNER, new RoomCreateCommand("옵션", day, day, TransportMode.CAR)).id();
		Map<String, Object> options = new LastApplied("key", 2, 9, "hash", "key-hash", 10).addTo(
			new ScheduleOptions(List.of(new DayOption(day, LocalTime.of(9, 0), LocalTime.of(18, 0))),
				new NamedPoint("강릉역", new BigDecimal("37.7643"), new BigDecimal("128.8996")), null,
				TravelLeg.found(27, new BigDecimal("1234.50"), "KAKAO_CAR", Instant.parse("2026-11-05T00:00:00Z")),
				List.of(new UnassignedPlace(19L), new UnassignedPlace(3_000_000_000L))).toMap());
		int before = jdbc.queryForObject("SELECT version FROM travel_rooms WHERE id = ?", Integer.class, roomId);

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			TravelRoom room = entityManager.find(TravelRoom.class, roomId);
			room.updateScheduleOptions(options, Instant.now());
			entityManager.flush();
		});

		assertThat(jdbc.queryForObject("SELECT version FROM travel_rooms WHERE id = ?", Integer.class, roomId))
			.isEqualTo(before + 1);
	}
}
