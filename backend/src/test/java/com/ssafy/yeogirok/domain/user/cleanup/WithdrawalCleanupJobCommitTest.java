package com.ssafy.yeogirok.domain.user.cleanup;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.footprint.repository.FootprintRepository;
import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.room.service.WithdrawnMemberRooms;
import com.ssafy.yeogirok.domain.statistics.PreservedVisit;
import com.ssafy.yeogirok.domain.statistics.PreservedVisitCounts;
import com.ssafy.yeogirok.domain.statistics.WithdrawnFootprints;
import com.ssafy.yeogirok.support.JpaPreservedVisitCounts;
import com.ssafy.yeogirok.support.TestUsers;
import com.ssafy.yeogirok.support.WithdrawalJpaTestConfig;

/**
 * 정리 작업은 대상마다 따로 커밋하고, 한 대상이 실패해도 그 대상만 롤백한 채 나머지를 계속한다. 실제 커밋을 봐야 해서 테스트
 * 트랜잭션을 쓰지 않고, 만든 행은 끝나고 지운다. 보존분 저장이 남반구 좌표에서 실패하도록 바꿔 한 회원의 정리를 일부러 깨뜨린다.
 */
@DataJpaTest
@Import({WithdrawalCleanupJob.class, WithdrawnFootprints.class, WithdrawnMemberRooms.class, RoomTestConfig.class,
	WithdrawalJpaTestConfig.class, WithdrawalCleanupJobCommitTest.Config.class})
@TestPropertySource(locations = "classpath:withdrawal-test.properties")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WithdrawalCleanupJobCommitTest {

	static final long FAILING = 3201L;
	static final long CLEANED = 3202L;
	static final long ROOM_OWNER = 3203L;

	@TestConfiguration
	static class Config {

		/** 남반구(위도 < 0) 칸이 섞이면 실패한다. 공용 JPA 구현에 저장한 뒤 실패해 트랜잭션이 롤백된다. */
		@Bean
		@Primary
		PreservedVisitCounts failingInSouth(JpaPreservedVisitCounts delegate) {
			return new PreservedVisitCounts() {
				@Override
				public void add(List<PreservedVisit> visits, Instant now) {
					delegate.add(visits, now);
					if (visits.stream().anyMatch(visit -> visit.centerLatitude().signum() < 0)) {
						throw new IllegalStateException("보존분 저장 실패(테스트)");
					}
				}
			};
		}
	}

	@Autowired
	WithdrawalCleanupJob job;
	@Autowired
	FootprintRepository footprintRepository;
	@Autowired
	RoomService roomService;
	@Autowired
	DataSource dataSource;
	JdbcTemplate jdbc;
	Long roomId;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
		for (long userId : List.of(FAILING, CLEANED, ROOM_OWNER)) {
			TestUsers.insertActive(jdbc, userId);
		}
		footprintRepository.save(footprintOf(FAILING).latitude(new BigDecimal("-30.0012345")).build());
		footprintRepository.save(footprintOf(CLEANED).build());
		roomId = roomService.create(ROOM_OWNER, new RoomCreateCommand("빈 방", null, null, TransportMode.CAR)).id();
		// 셋 다 이 정리가 생기기 전에 탈퇴한 상태.
		jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id IN (?, ?, ?)", FAILING, CLEANED, ROOM_OWNER);
	}

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM footprint_statistics");
		jdbc.update("DELETE FROM room_members WHERE room_id = ?", roomId);
		jdbc.update("DELETE FROM travel_rooms WHERE id = ?", roomId);
		for (long userId : List.of(FAILING, CLEANED, ROOM_OWNER)) {
			TestUsers.delete(jdbc, userId);
		}
	}

	@Test
	void 한_회원의_정리가_실패해도_그_회원만_롤백하고_나머지는_커밋한다() {
		job.cleanUp();

		// 실패한 회원: 보존분과 원본 삭제가 함께 롤백돼 원본이 그대로다.
		assertThat(footprintCount(FAILING)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM footprint_statistics WHERE center_latitude < 0",
			Long.class)).isZero();
		// 다른 회원과 빈 방은 따로 커밋됐다.
		assertThat(footprintCount(CLEANED)).isZero();
		assertThat(jdbc.queryForObject("SELECT sum(visit_count) FROM footprint_statistics", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM travel_rooms WHERE id = ?", Boolean.class,
			roomId)).isTrue();
	}

	@Test
	void 실패한_회원은_다음_주기에_다시_잡힌다() {
		job.cleanUp();

		assertThat(footprintRepository.findWithdrawnUserIds()).containsExactly(FAILING);
	}

	private long footprintCount(long userId) {
		return jdbc.queryForObject("SELECT count(*) FROM footprints WHERE user_id = ?", Long.class, userId);
	}
}
