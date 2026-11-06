package com.ssafy.yeogirok.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomInvitationResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomJoinResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRemoveResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRestoreResponse;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.TestUsers;

/**
 * 방 행 잠금으로 참여·나가기·초대 링크 생성·후보 장소 변경이 한 줄로 서는지 본다. 요청마다 실제로 커밋해야 하므로 테스트 트랜잭션을 쓰지 않고,
 * 만든 행은 끝나고 지운다.
 */
@DataJpaTest
@Import(RoomTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoomConcurrencyTest {

	static final long OWNER = 5000L;
	/** 동시 참여자 5001~5020. */
	static final long CROWD_START = 5001L;
	static final int CROWD = 20;

	@Autowired
	RoomService roomService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	RoomPlaceService roomPlaceService;
	@Autowired
	DataSource dataSource;

	JdbcTemplate jdbc;
	Long roomId;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
		TestUsers.insertActive(jdbc, OWNER);
		LongStream.range(CROWD_START, CROWD_START + CROWD).forEach(id -> TestUsers.insertActive(jdbc, id));
		roomId = roomService.create(OWNER, new RoomCreateCommand("동시성", null, null, TransportMode.CAR)).id();
	}

	@AfterEach
	void tearDown() {
		// 직접 추가한 장소 행은 이 방 후보만 참조하므로 후보를 지운 뒤 같이 지운다.
		List<Long> placeIds = jdbc.queryForList("SELECT place_id FROM room_places WHERE room_id = ?", Long.class,
			roomId);
		jdbc.update("DELETE FROM room_places WHERE room_id = ?", roomId);
		placeIds.forEach(placeId -> jdbc.update("DELETE FROM places WHERE id = ?", placeId));
		jdbc.update("DELETE FROM room_members WHERE room_id = ?", roomId);
		jdbc.update("DELETE FROM travel_rooms WHERE id = ?", roomId);
		jdbc.update("DELETE FROM users WHERE id BETWEEN ? AND ?", OWNER, CROWD_START + CROWD);
	}

	@Test
	void 아홉_명인_방에_두_명이_동시에_참여하면_한_명만_성공한다() throws Exception {
		String token = inviteToken();
		for (long id = CROWD_START; id < CROWD_START + 8; id++) {
			roomInvitationService.accept(id, token);
		}
		assertThat(currentMembers()).isEqualTo(9);

		List<Object> outcomes = runConcurrently(List.of(
			() -> roomInvitationService.accept(CROWD_START + 8, token),
			() -> roomInvitationService.accept(CROWD_START + 9, token)));

		assertThat(outcomes).filteredOn(RoomJoinResponse.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(ExceptionType.ROOM_FULL::equals).hasSize(1);
		assertThat(currentMembers()).isEqualTo(10);
	}

	@Test
	void 스무_명이_동시에_참여해도_현재_참여자는_10명을_넘지_않는다() throws Exception {
		String token = inviteToken();
		List<Callable<Object>> joins = new ArrayList<>();
		for (long id = CROWD_START; id < CROWD_START + CROWD; id++) {
			long userId = id;
			joins.add(() -> roomInvitationService.accept(userId, token));
		}

		List<Object> outcomes = runConcurrently(joins);

		assertThat(outcomes).filteredOn(RoomJoinResponse.class::isInstance).hasSize(9);
		assertThat(outcomes).filteredOn(ExceptionType.ROOM_FULL::equals).hasSize(CROWD - 9);
		assertThat(currentMembers()).isEqualTo(10);
	}

	/**
	 * 마지막 참여자 퇴장과 새 참여가 겹쳐도 "삭제된 방에 현재 참여자가 남는" 상태는 생기지 않는다.
	 * 나가기가 먼저면 방이 삭제되고 참여는 RO001, 참여가 먼저면 방이 남고 참여자는 1명이다.
	 */
	@RepeatedTest(5)
	void 마지막_참여자_퇴장과_새_참여가_겹쳐도_삭제된_방에_참여자가_남지_않는다() throws Exception {
		String token = inviteToken();

		List<Object> outcomes = runConcurrently(List.of(
			() -> {
				roomService.leave(OWNER, roomId);
				return "LEFT";
			},
			() -> roomInvitationService.accept(CROWD_START, token)));

		boolean deleted = jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM travel_rooms WHERE id = ?",
			Boolean.class, roomId);
		assertThat(outcomes.getFirst()).isEqualTo("LEFT");
		if (deleted) {
			assertThat(outcomes.get(1)).isEqualTo(ExceptionType.ROOM_NOT_FOUND);
			assertThat(currentMembers()).isZero();
		} else {
			assertThat(outcomes.get(1)).isInstanceOf(RoomJoinResponse.class);
			assertThat(currentMembers()).isEqualTo(1);
		}
	}

	@Test
	void 초대_링크를_동시에_만들어도_링크는_하나다() throws Exception {
		String token = inviteToken();
		for (long id = CROWD_START; id < CROWD_START + 4; id++) {
			roomInvitationService.accept(id, token);
		}
		jdbc.update("UPDATE travel_rooms SET invite_token_encrypted = NULL WHERE id = ?", roomId);

		List<Callable<Object>> creates = new ArrayList<>();
		creates.add(() -> roomInvitationService.getOrCreateInvitation(OWNER, roomId));
		for (long id = CROWD_START; id < CROWD_START + 4; id++) {
			long userId = id;
			creates.add(() -> roomInvitationService.getOrCreateInvitation(userId, roomId));
		}

		List<Object> outcomes = runConcurrently(creates);

		assertThat(outcomes).allMatch(RoomInvitationResponse.class::isInstance);
		List<RoomInvitationResponse> responses = outcomes.stream().map(RoomInvitationResponse.class::cast).toList();
		assertThat(responses).filteredOn(RoomInvitationResponse::created).hasSize(1);
		assertThat(responses).extracting(RoomInvitationResponse::inviteToken).containsOnly(
			responses.getFirst().inviteToken());
	}

	/* ── 후보 장소 ──────────────────────────────────────────── */

	@Test
	void 같은_후보를_동시에_빼도_모두_성공하고_version은_한_번만_오른다() throws Exception {
		Long placeId = addPlace();
		int before = roomVersion();

		List<Object> outcomes = runConcurrently(LongStream.range(0, 5)
			.<Callable<Object>>mapToObj(i -> () -> roomPlaceService.remove(OWNER, roomId, placeId, before))
			.toList());

		assertThat(outcomes).allMatch(RoomPlaceRemoveResponse.class::isInstance);
		assertThat(outcomes).map(outcome -> ((RoomPlaceRemoveResponse)outcome).version()).containsOnly(before + 1);
		assertThat(roomVersion()).isEqualTo(before + 1);
	}

	@Test
	void 같은_후보를_동시에_복원해도_모두_성공하고_version은_한_번만_오른다() throws Exception {
		Long placeId = addPlace();
		roomPlaceService.remove(OWNER, roomId, placeId, roomVersion());
		int before = roomVersion();

		List<Object> outcomes = runConcurrently(LongStream.range(0, 5)
			.<Callable<Object>>mapToObj(i -> () -> roomPlaceService.restore(OWNER, roomId, placeId, before))
			.toList());

		assertThat(outcomes).allMatch(RoomPlaceRestoreResponse.class::isInstance);
		assertThat(roomVersion()).isEqualTo(before + 1);
		assertThat(jdbc.queryForObject("SELECT removed_at IS NULL FROM room_places WHERE id = ?", Boolean.class,
			placeId)).isTrue();
	}

	@Test
	void 여러_참여자가_동시에_직접_추가하면_모두_저장되고_version도_그만큼_오른다() throws Exception {
		String token = inviteToken();
		for (long id = CROWD_START; id < CROWD_START + 4; id++) {
			roomInvitationService.accept(id, token);
		}
		int before = roomVersion();

		List<Callable<Object>> adds = new ArrayList<>();
		adds.add(() -> addPlaceBy(OWNER));
		for (long id = CROWD_START; id < CROWD_START + 4; id++) {
			long userId = id;
			adds.add(() -> addPlaceBy(userId));
		}
		List<Object> outcomes = runConcurrently(adds);

		assertThat(outcomes).allMatch(CustomPlaceCreateResponse.class::isInstance);
		assertThat(outcomes).map(outcome -> ((CustomPlaceCreateResponse)outcome).version())
			.containsExactlyInAnyOrder(before + 1, before + 2, before + 3, before + 4, before + 5);
		assertThat(roomVersion()).isEqualTo(before + 5);
	}

	private Long addPlace() {
		return addPlaceBy(OWNER).roomPlace().id();
	}

	private CustomPlaceCreateResponse addPlaceBy(long userId) {
		return roomPlaceService.addCustomPlace(userId, roomId,
			new CustomPlaceCreateCommand("경포대", new BigDecimal("37.7519000"), new BigDecimal("128.8761000"), null));
	}

	private int roomVersion() {
		return jdbc.queryForObject("SELECT version FROM travel_rooms WHERE id = ?", Integer.class, roomId);
	}

	private String inviteToken() {
		return roomInvitationService.getOrCreateInvitation(OWNER, roomId).inviteToken();
	}

	private int currentMembers() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM room_members WHERE room_id = ? AND left_at IS NULL",
			Integer.class, roomId);
	}

	/** 모든 작업을 한꺼번에 출발시킨다. 성공은 반환값, 업무 오류는 ExceptionType 으로 모은다(순서는 작업 순서). */
	private static List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					try {
						return task.call();
					} catch (BusinessException e) {
						return e.getExceptionType();
					}
				}));
			}
			start.countDown();
			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> future : futures) {
				outcomes.add(future.get(20, TimeUnit.SECONDS));
			}
			return outcomes;
		} finally {
			executor.shutdownNow();
		}
	}
}
