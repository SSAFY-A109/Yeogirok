package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.LEGAL_DONG;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitAddResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitCreationType;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;

/** API-066 실제 방문 수동 추가. 요청 형식 오류는 RecordControllerTest 가 본다. */
@DataJpaTest
@Import(RecordTestConfig.class)
class VisitCommandServiceTest {

	static final Long MEMBER = 7001L;
	static final Long LEFT = 7002L;
	static final Long STRANGER = 7003L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");
	static final LocalDate DAY2 = LocalDate.parse("2026-11-02");
	static final Instant AT = Instant.parse("2026-11-01T01:00:00Z");
	static final VisitPlaceInput.CustomPlace PIN = new VisitPlaceInput.CustomPlace("경포 해변",
		new BigDecimal("37.7951000"), new BigDecimal("128.9087000"), null);

	@Autowired
	VisitCommandService visitCommandService;
	@Autowired
	FixedLegalDongResolver legalDongResolver;
	@Autowired
	TestEntityManager em;
	@Autowired
	DataSource dataSource;

	Long recordId;

	@BeforeEach
	void setUp() {
		legalDongResolver.willReturn(LEGAL_DONG);
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY2)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), MEMBER, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		recordId = em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
		em.flush();
	}

	/** 투어API 장소 담기(API-029)가 아직 없어 행을 바꿔 만든다. */
	private Long tourPlace(String providerPlaceId) {
		Long id = em.persistAndGetId(Place.manual("투어 장소", new BigDecimal("37.75"), new BigDecimal("128.87"), null,
			null, null, NOW), Long.class);
		em.flush();
		em.getEntityManager().createNativeQuery(
				"update places set provider = 'TOUR_API', provider_place_id = ?1 where id = ?2")
			.setParameter(1, providerPlaceId).setParameter(2, id).executeUpdate();
		em.clear();
		return id;
	}

	private static VisitAddCommand.Item item(VisitPlaceInput place, LocalDate date, int order) {
		return new VisitAddCommand.Item(place, date, AT, order);
	}

	private VisitAddResponse add(int version, VisitAddCommand.Item... items) {
		VisitAddResponse response = visitCommandService.add(MEMBER, recordId, new VisitAddCommand(version, List.of(items)));
		em.flush();
		em.clear();
		return response;
	}

	/** @return 날짜·순서대로 "날짜:순서:id" */
	private List<String> layout() {
		return em.getEntityManager().createQuery("select v from ActualVisit v where v.recordId = :recordId"
				+ " order by v.visitDate, v.position", ActualVisit.class)
			.setParameter("recordId", recordId).getResultList().stream()
			.map(v -> v.getVisitDate() + ":" + v.getPosition() + ":" + v.getId()).toList();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 저장된_투어API_장소와_직접_입력_장소로_방문을_만들고_기록_version이_오른다() {
		Long tourId = tourPlace("tour-1");

		VisitAddResponse response = add(1, item(new VisitPlaceInput.TourPlace("tour-1"), DAY1, 1), item(PIN, DAY1, 2));

		assertThat(response.visitIds()).hasSize(2);
		assertThat(response.version()).isEqualTo(2);
		assertThat(response.status()).isEqualTo(RecordStatus.DRAFT);
		ActualVisit tour = em.find(ActualVisit.class, response.visitIds().get(0));
		assertThat(tour.getPlaceId()).isEqualTo(tourId);
		assertThat(tour.getCreationType()).isEqualTo(VisitCreationType.USER);
		assertThat(tour.isPublicSelected()).isTrue();
		assertThat(tour.getVisitedAt()).isEqualTo(AT);
		assertThat(tour.getPosition()).isEqualTo(1);
		Place pin = em.find(Place.class, em.find(ActualVisit.class, response.visitIds().get(1)).getPlaceId());
		assertThat(pin.getProvider()).isEqualTo(PlaceProvider.MANUAL);
		assertThat(pin.getName()).isEqualTo("경포 해변");
		assertThat(pin.getRegionName()).isEqualTo("강문동");
		assertThat(legalDongResolver.calls()).isEqualTo(1);
		assertThat(em.find(TravelRecord.class, recordId).getVersion()).isEqualTo(2);
	}

	@Test
	void 순서가_겹치면_기존_방문을_뒤로_밀고_큰_순서는_맨_뒤에_붙는다() {
		List<Long> first = add(1, item(PIN, DAY1, 1), item(PIN, DAY1, 2)).visitIds();

		List<Long> second = add(2, item(PIN, DAY1, 1), item(PIN, DAY1, 99), item(PIN, DAY2, 3)).visitIds();

		assertThat(layout()).containsExactly(
			DAY1 + ":1:" + second.get(0), DAY1 + ":2:" + first.get(0), DAY1 + ":3:" + first.get(1),
			DAY1 + ":4:" + second.get(1), DAY2 + ":1:" + second.get(2));
	}

	/** Flyway V1 의 DEFERRABLE INITIALLY IMMEDIATE 유니크(문장마다 검사)를 걸어도 순서를 밀 수 있다. */
	@Test
	void 날짜_안_순서_유니크가_있어도_기존_방문을_뒤로_민다() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assumeTrue("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()),
				"PostgreSQL 에서만 확인한다");
		}
		em.getEntityManager().createNativeQuery("alter table actual_visits add constraint uq_test_visit_position"
			+ " unique (record_id, visit_date, position) deferrable initially immediate").executeUpdate();
		List<Long> first = add(1, item(PIN, DAY1, 1), item(PIN, DAY1, 2)).visitIds();

		List<Long> second = add(2, item(PIN, DAY1, 1)).visitIds();

		assertThat(layout()).containsExactly(DAY1 + ":1:" + second.get(0), DAY1 + ":2:" + first.get(0),
			DAY1 + ":3:" + first.get(1));
	}

	@Test
	void 완성한_기록에_추가하면_DRAFT로_돌아간다() {
		em.find(TravelRecord.class, recordId).complete(NOW);
		em.flush();

		VisitAddResponse response = add(2, item(PIN, DAY1, 1));

		assertThat(response.status()).isEqualTo(RecordStatus.DRAFT);
		assertThat(response.version()).isEqualTo(3);
	}

	@Test
	void version이_다르면_C004이고_아무것도_저장하지_않는다() {
		assertError(() -> add(2, item(PIN, DAY1, 1)), ExceptionType.VERSION_MISMATCH);
		assertThat(layout()).isEmpty();
	}

	@Test
	void places_에_없는_투어API_장소는_C003() {
		assertError(() -> add(1, item(new VisitPlaceInput.TourPlace("unknown"), DAY1, 1)),
			ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 나간_사람과_비참여자는_RE003이고_법정동을_조회하지_않는다_없는_기록은_RE001() {
		for (Long user : new Long[] {LEFT, STRANGER}) {
			assertError(() -> visitCommandService.add(user, recordId, new VisitAddCommand(1, List.of(item(PIN, DAY1, 1)))),
				ExceptionType.RECORD_EDIT_FORBIDDEN);
		}
		assertThat(legalDongResolver.calls()).isZero();
		assertError(() -> visitCommandService.add(MEMBER, recordId + 100,
			new VisitAddCommand(1, List.of(item(new VisitPlaceInput.TourPlace("t"), DAY1, 1)))),
			ExceptionType.RECORD_NOT_FOUND);
	}

	@Test
	void 멱등_재전송은_만든_방문_ID와_지금_기록_상태를_주고_현재_권한을_다시_본다() {
		List<Long> visitIds = add(1, item(PIN, DAY1, 1)).visitIds();

		VisitAddResponse again = visitCommandService.getAdded(MEMBER, recordId, visitIds);

		assertThat(again.visitIds()).isEqualTo(visitIds);
		assertThat(again.version()).isEqualTo(2);
		assertError(() -> visitCommandService.getAdded(LEFT, recordId, visitIds), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}
}
