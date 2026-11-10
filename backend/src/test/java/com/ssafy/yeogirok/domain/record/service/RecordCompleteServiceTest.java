package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.RecordCompleteResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** API-071 여행기 완성. */
@DataJpaTest
@Import(RecordTestConfig.class)
class RecordCompleteServiceTest {

	static final Long ME = 9101L;
	static final Long LEFT = 9102L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");

	@Autowired
	RecordCompleteService recordCompleteService;
	@Autowired
	TestEntityManager em;

	Long recordId;

	@BeforeEach
	void setUp() {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY1)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		recordId = em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
		em.flush();
	}

	private void visit(Long placeId) {
		ActualVisit visit = ActualVisit.manual(recordId, placeId, DAY1, NOW, NOW);
		visit.moveTo(1, NOW);
		em.persist(visit);
		em.flush();
	}

	private Long place() {
		return em.persistAndGetId(Place.manual("경포대", new BigDecimal("37.75"), new BigDecimal("128.87"), null, null,
			null, NOW), Long.class);
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 방문이_있으면_사진_이야기_없이도_완성하고_version이_오른다() {
		visit(place());

		RecordCompleteResponse response = recordCompleteService.complete(ME, recordId, 1);

		assertThat(response).isEqualTo(new RecordCompleteResponse(recordId, RecordStatus.COMPLETED, 2));
		em.flush();
		em.clear();
		assertThat(em.find(TravelRecord.class, recordId).getStatus()).isEqualTo(RecordStatus.COMPLETED);
	}

	@Test
	void 이미_완성했으면_version을_다시_올리지_않는다() {
		visit(place());
		recordCompleteService.complete(ME, recordId, 1);

		assertThat(recordCompleteService.complete(ME, recordId, 2).version()).isEqualTo(2);
		assertThat(recordCompleteService.getCompleted(ME, recordId).version()).isEqualTo(2);
	}

	@Test
	void 실제_방문이_없으면_RE016() {
		assertError(() -> recordCompleteService.complete(ME, recordId, 1), ExceptionType.RECORD_NOT_COMPLETABLE);
	}

	@Test
	void 장소가_없는_방문이_있으면_RE016() {
		visit(place());
		visit(place() + 1000);

		assertError(() -> recordCompleteService.complete(ME, recordId, 1), ExceptionType.RECORD_NOT_COMPLETABLE);
	}

	@Test
	void version이_다르면_C004_나간_사람은_RE003() {
		visit(place());

		assertError(() -> recordCompleteService.complete(ME, recordId, 2), ExceptionType.VERSION_MISMATCH);
		assertError(() -> recordCompleteService.complete(LEFT, recordId, 1), ExceptionType.RECORD_EDIT_FORBIDDEN);
		assertError(() -> recordCompleteService.getCompleted(LEFT, recordId), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}
}
