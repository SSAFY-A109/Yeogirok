package com.ssafy.yeogirok.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand.ItemEdit;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.FakeTravelTimeProvider;
import com.ssafy.yeogirok.support.TestUsers;

/**
 * API-040 은 이동 시간을 방 잠금 밖에서 계산하고, 잠근 뒤 version 을 다시 확인해 저장한다. 계산하는 사이에 방·후보·일정이
 * 바뀌면 늦게 저장하려는 쪽이 C004 로 끝나고 낡은 계산 결과는 저장되지 않는지 본다. 요청마다 실제로 커밋해야 하므로 테스트
 * 트랜잭션을 쓰지 않고, 만든 행은 끝나고 지운다.
 */
@DataJpaTest
@Import(RoomTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleConcurrencyTest {

	static final long OWNER = 8000L;
	/** RoomTestConfig 시계(2026-11-05) 기준 오늘. */
	static final LocalDate DAY = LocalDate.of(2026, 11, 5);

	@Autowired
	ScheduleService scheduleService;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomPlaceService roomPlaceService;
	@Autowired
	FakeTravelTimeProvider travelTimeProvider;
	@Autowired
	DataSource dataSource;

	JdbcTemplate jdbc;
	ExecutorService executor;
	Long roomId;
	Long placeA;
	Long placeB;
	Long placeC;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
		executor = Executors.newSingleThreadExecutor();
		travelTimeProvider.willReturn(RoomTestConfig.TRAVEL_MINUTES);
		TestUsers.insertActive(jdbc, OWNER);
		roomId = roomService.create(OWNER, new RoomCreateCommand("동시 편집", DAY, DAY, TransportMode.CAR)).id();
		placeA = addPlace("경포대");
		placeB = addPlace("안목해변");
		placeC = addPlace("오죽헌");
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
		travelTimeProvider.willReturn(RoomTestConfig.TRAVEL_MINUTES);
		List<Long> placeIds = jdbc.queryForList("SELECT place_id FROM room_places WHERE room_id = ?", Long.class,
			roomId);
		jdbc.update("DELETE FROM schedule_items WHERE room_id = ?", roomId);
		jdbc.update("DELETE FROM room_places WHERE room_id = ?", roomId);
		placeIds.forEach(placeId -> jdbc.update("DELETE FROM places WHERE id = ?", placeId));
		jdbc.update("DELETE FROM room_members WHERE room_id = ?", roomId);
		jdbc.update("DELETE FROM travel_rooms WHERE id = ?", roomId);
		jdbc.update("DELETE FROM users WHERE id = ?", OWNER);
	}

	private Long addPlace(String name) {
		return roomPlaceService.addCustomPlace(OWNER, roomId, new CustomPlaceCreateCommand(name,
			new BigDecimal("37.7519000"), new BigDecimal("128.8761000"), null)).roomPlace().id();
	}

	private int version() {
		return jdbc.queryForObject("SELECT version FROM travel_rooms WHERE id = ?", Integer.class, roomId);
	}

	private int scheduleItems() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM schedule_items WHERE room_id = ?", Integer.class, roomId);
	}

	private static ItemEdit item(Long placeId, int order) {
		return new ItemEdit(null, placeId, DAY, order, 60, null);
	}

	/** 두 항목을 넣는 편집을 시작하고, 둘째 항목의 이동 시간을 계산하는 중에 멈춰 둔다. */
	private Future<Object> startPausedEdit(CountDownLatch release) throws InterruptedException {
		CountDownLatch entered = new CountDownLatch(1);
		travelTimeProvider.pauseNextCall(entered, release);
		int version = version();
		Future<Object> edit = executor.submit(() -> scheduleService.update(OWNER, roomId,
			new ScheduleUpdateCommand(version, List.of(item(placeA, 1), item(placeB, 2)), List.of())));
		assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
		return edit;
	}

	private static void assertVersionMismatch(Future<Object> edit) {
		assertThatThrownBy(() -> edit.get(10, TimeUnit.SECONDS))
			.isInstanceOf(ExecutionException.class)
			.cause().isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 계산하는_사이에_후보를_빼면_편집은_C004로_끝나고_저장하지_않는다() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Future<Object> edit = startPausedEdit(release);

		roomPlaceService.remove(OWNER, roomId, placeC, version());
		release.countDown();

		assertVersionMismatch(edit);
		assertThat(scheduleItems()).isZero();
	}

	@Test
	void 계산하는_사이에_다른_편집이_먼저_저장되면_늦은_편집은_C004로_끝난다() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		Future<Object> slow = startPausedEdit(release);

		// 항목 하나는 출발지가 없어 길찾기를 부르지 않는다(멈추지 않는다).
		scheduleService.update(OWNER, roomId,
			new ScheduleUpdateCommand(version(), List.of(item(placeC, 1)), List.of()));
		release.countDown();

		assertVersionMismatch(slow);
		assertThat(jdbc.queryForList("SELECT room_place_id FROM schedule_items WHERE room_id = ?", Long.class,
			roomId)).containsExactly(placeC);
		assertThat(travelTimeProvider.calls()).hasSize(1);
	}
}
