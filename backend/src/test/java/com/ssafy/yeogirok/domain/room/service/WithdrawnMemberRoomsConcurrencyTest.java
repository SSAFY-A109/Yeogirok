package com.ssafy.yeogirok.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.support.TestUsers;

/**
 * 마지막 두 참여자가 동시에 탈퇴해도 방이 지워지는지 본다. 두 탈퇴가 서로의 회원 상태 변경을 커밋 전에 보지 못하는 순간(둘 다
 * 상태를 바꾼 뒤 방을 세기 직전)에 맞춰 출발시킨다. 방 행 잠금이 없으면 둘 다 상대를 참여자로 세어 방이 남는다. 실제 커밋이
 * 필요해 테스트 트랜잭션을 쓰지 않고, 만든 행은 끝나고 지운다.
 */
@DataJpaTest
@Import({RoomTestConfig.class, WithdrawnMemberRooms.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WithdrawnMemberRoomsConcurrencyTest {

	static final long OWNER = 5101L;
	static final long FRIEND = 5102L;

	@Autowired
	WithdrawnMemberRooms withdrawnMemberRooms;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	DataSource dataSource;
	JdbcTemplate jdbc;
	Long roomId;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
		TestUsers.insertActive(jdbc, OWNER);
		TestUsers.insertActive(jdbc, FRIEND);
		roomId = roomService.create(OWNER, new RoomCreateCommand("동시 탈퇴", null, null, TransportMode.CAR)).id();
		String token = roomInvitationService.getOrCreateInvitation(OWNER, roomId).inviteToken();
		roomInvitationService.accept(FRIEND, token);
	}

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM room_members WHERE room_id = ?", roomId);
		jdbc.update("DELETE FROM travel_rooms WHERE id = ?", roomId);
		TestUsers.delete(jdbc, OWNER);
		TestUsers.delete(jdbc, FRIEND);
	}

	@Test
	void 마지막_두_참여자가_동시에_탈퇴해도_방이_지워진다() throws Exception {
		CyclicBarrier bothWithdrawn = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<?>> results = List.of(
				executor.submit(() -> withdraw(OWNER, bothWithdrawn)),
				executor.submit(() -> withdraw(FRIEND, bothWithdrawn)));
			for (Future<?> result : results) {
				result.get(10, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM travel_rooms WHERE id = ?", Boolean.class,
			roomId)).isTrue();
	}

	/** 탈퇴 트랜잭션처럼 회원 상태를 먼저 바꾸고(커밋 전), 두 탈퇴가 모두 여기까지 온 뒤 방을 정리한다. */
	private void withdraw(long userId, CyclicBarrier bothWithdrawn) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", userId);
			try {
				bothWithdrawn.await(5, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			withdrawnMemberRooms.onUserWithdrawn(
				new UserWithdrawnEvent(userId, OAuthProvider.KAKAO, "kakao-" + userId, null));
		});
	}
}
