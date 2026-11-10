package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateCommand;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import jakarta.persistence.EntityManager;

/**
 * 방문 삭제(067)와 그 방문에 사진 연결(068)이 동시에 와도, 기록 행 잠금으로 한 줄로 서서 사진이 지워진 방문에 붙지 않는다
 * (노션 067 "경합 중 연결 사진을 놓치지 않는다"). 요청마다 실제로 커밋해야 하므로 테스트 트랜잭션을 쓰지 않고, 만든 행은 끝나고 지운다.
 */
@DataJpaTest
@Import(RecordTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecordConcurrencyTest {

	static final Long ME = 9301L;
	static final Long FRIEND = 9302L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");

	@Autowired
	VisitCommandService visitCommandService;
	@Autowired
	VisitPhotoService visitPhotoService;
	@Autowired
	EntityManager em;
	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	DataSource dataSource;

	TransactionTemplate tx;
	Long roomId;
	Long recordId;
	Long placeId;
	Long visitId;
	Long imageId;

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
		tx.executeWithoutResult(status -> {
			TravelRoom room = TravelRoom.builder().title("동시성").startDate(DAY1).endDate(DAY1)
				.transportMode(TransportMode.CAR).createdAt(NOW).build();
			em.persist(room);
			roomId = room.getId();
			em.persist(RoomMember.creator(roomId, ME, NOW));
			em.persist(RoomMember.joined(roomId, FRIEND, NOW));
			TravelRecord record = TravelRecord.create(roomId, "동시성", NOW);
			em.persist(record);
			recordId = record.getId();
			Place place = Place.manual("경포대", new BigDecimal("37.75"), new BigDecimal("128.87"), null, null, null, NOW);
			em.persist(place);
			placeId = place.getId();
			ActualVisit visit = ActualVisit.manual(recordId, placeId, DAY1, NOW, NOW);
			visit.moveTo(1, NOW);
			em.persist(visit);
			visitId = visit.getId();
			VisitImage image = VisitImage.unlinked(recordId, FRIEND, "record-photo/race", NOW);
			em.persist(image);
			imageId = image.getId();
		});
	}

	@AfterEach
	void tearDown() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("DELETE FROM visit_images WHERE record_id = ?", recordId);
		jdbc.update("DELETE FROM actual_visits WHERE record_id = ?", recordId);
		jdbc.update("DELETE FROM places WHERE id = ?", placeId);
		jdbc.update("DELETE FROM travel_records WHERE id = ?", recordId);
		jdbc.update("DELETE FROM room_members WHERE room_id = ?", roomId);
		jdbc.update("DELETE FROM travel_rooms WHERE id = ?", roomId);
	}

	@RepeatedTest(5)
	void 방문_삭제와_사진_연결이_동시에_와도_사진이_지워진_방문에_붙지_않는다() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Exception> remove = pool.submit(attempt(start, () -> visitCommandService.update(ME, recordId,
				new VisitUpdateCommand(1, List.of(), List.of(visitId), false))));
			Future<Exception> link = pool.submit(attempt(start,
				() -> visitPhotoService.link(FRIEND, recordId, visitId, List.of(imageId))));
			start.countDown();
			Exception removeError = remove.get(10, TimeUnit.SECONDS);
			Exception linkError = link.get(10, TimeUnit.SECONDS);

			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			boolean visitExists = jdbc.queryForObject("SELECT COUNT(*) FROM actual_visits WHERE id = ?", Long.class,
				visitId) == 1;
			Long linkedVisit = jdbc.queryForObject("SELECT visit_id FROM visit_images WHERE id = ?", Long.class, imageId);
			if (visitExists) {
				// 연결이 먼저: 삭제는 낡은 version 이라 C004, 사진은 방문에 붙어 있다.
				assertThat(linkError).isNull();
				assertThat(errorType(removeError)).isEqualTo(ExceptionType.VERSION_MISMATCH);
				assertThat(linkedVisit).isEqualTo(visitId);
			} else {
				// 삭제가 먼저: 연결은 방문이 없어 RE013, 사진은 보관함에 남는다.
				assertThat(removeError).isNull();
				assertThat(errorType(linkError)).isEqualTo(ExceptionType.VISIT_NOT_FOUND);
				assertThat(linkedVisit).isNull();
			}
		} finally {
			pool.shutdownNow();
		}
	}

	private static Callable<Exception> attempt(CountDownLatch start, Runnable call) {
		return () -> {
			start.await();
			try {
				call.run();
				return null;
			} catch (Exception e) {
				return e;
			}
		};
	}

	private static ExceptionType errorType(Exception e) {
		return e instanceof BusinessException business ? business.getExceptionType() : null;
	}
}
