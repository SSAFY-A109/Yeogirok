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
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.ActualVisitView;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.entity.VisitStory;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;

/** API-067 실제 방문 수정·삭제. 요청 형식 오류는 RecordControllerTest 가 본다. */
@DataJpaTest
@Import(RecordTestConfig.class)
class VisitUpdateServiceTest {

	static final Long MEMBER = 8001L;
	static final Long FRIEND = 8002L;
	static final Long LEFT = 8003L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");
	static final LocalDate DAY2 = LocalDate.parse("2026-11-02");
	static final Instant AT1 = Instant.parse("2026-11-01T01:00:00Z");
	static final Instant AT2 = Instant.parse("2026-11-02T01:00:00Z");

	@Autowired
	VisitCommandService visitCommandService;
	@Autowired
	FixedLegalDongResolver legalDongResolver;
	@Autowired
	TestEntityManager em;
	@Autowired
	DataSource dataSource;

	Long recordId;
	Long placeId;
	ActualVisit a;
	ActualVisit b;
	ActualVisit c;

	@BeforeEach
	void setUp() {
		legalDongResolver.willReturn(LEGAL_DONG);
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY2)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), MEMBER, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		TravelRecord record = TravelRecord.create(room.getId(), "강릉 여행", NOW);
		record.attachReferencePlan(RecordTestConfig.referencePlan(room.getId(), DAY1, 501L, 502L));
		recordId = em.persist(record).getId();
		placeId = em.persist(Place.manual("경포대", new BigDecimal("37.7951000"), new BigDecimal("128.9087000"), null,
			null, null, NOW)).getId();
		a = visit(DAY1, 1);
		b = visit(DAY1, 2);
		c = visit(DAY1, 3);
		em.flush();
	}

	private ActualVisit visit(LocalDate date, int position) {
		ActualVisit visit = ActualVisit.manual(recordId, placeId, date, date.equals(DAY1) ? AT1 : AT2, NOW);
		visit.moveTo(position, NOW);
		return em.persist(visit);
	}

	/** 바꾸지 않는 값은 지금 값 그대로. */
	private static VisitUpdateCommand.Item keep(ActualVisit visit, Integer order) {
		return new VisitUpdateCommand.Item(visit.getId(), null, visit.getVisitDate(), visit.getVisitedAt(), order);
	}

	private VisitUpdateResponse update(int version, List<VisitUpdateCommand.Item> items, List<Long> remove,
		boolean confirm) {
		VisitUpdateResponse response = visitCommandService.update(MEMBER, recordId,
			new VisitUpdateCommand(version, items, remove, confirm));
		em.flush();
		em.clear();
		return response;
	}

	/** @return 날짜·순서대로 "날짜:순서:id" */
	private List<String> layout() {
		em.flush();
		em.clear();
		return em.getEntityManager().createQuery("select v from ActualVisit v where v.recordId = :recordId"
				+ " order by v.visitDate, v.position", ActualVisit.class)
			.setParameter("recordId", recordId).getResultList().stream()
			.map(v -> v.getVisitDate() + ":" + v.getPosition() + ":" + v.getId()).toList();
	}

	/** 방문에 그 방문만 쓰는 직접 입력 장소를 붙인다. @return 장소 ID */
	private Long ownPlace(ActualVisit visit) {
		Long id = em.persist(Place.manual("혼자 쓰는 곳", new BigDecimal("37.7000000"), new BigDecimal("128.9000000"),
			null, null, null, NOW)).getId();
		em.flush();
		em.getEntityManager().createNativeQuery("update actual_visits set place_id = ?1 where id = ?2")
			.setParameter(1, id).setParameter(2, visit.getId()).executeUpdate();
		em.clear();
		return id;
	}

	private long placeCount() {
		em.flush();
		return em.getEntityManager().createQuery("select count(p) from Place p", Long.class).getSingleResult();
	}

	private int version() {
		return em.find(TravelRecord.class, recordId).getVersion();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 장소와_시각을_바꾸면_방문의_place_id만_바꾸고_전체_방문을_돌려준다() {
		VisitPlaceInput.CustomPlace pin = new VisitPlaceInput.CustomPlace("안목 카페", new BigDecimal("37.7720000"),
			new BigDecimal("128.9470000"), "강원 강릉시 창해로");
		Instant later = Instant.parse("2026-11-01T03:00:00Z");

		VisitUpdateResponse response = update(1, List.of(new VisitUpdateCommand.Item(b.getId(), pin, DAY1, later, null)),
			List.of(), false);

		assertThat(response.version()).isEqualTo(2);
		assertThat(response.status()).isEqualTo(RecordStatus.DRAFT);
		assertThat(response.actualVisits()).extracting(ActualVisitView::id)
			.containsExactly(a.getId(), b.getId(), c.getId());
		ActualVisitView changed = response.actualVisits().get(1);
		assertThat(changed.place().name()).isEqualTo("안목 카페");
		assertThat(changed.place().address()).isEqualTo("강원 강릉시 창해로");
		assertThat(changed.visitedAt()).isEqualTo(later);
		assertThat(changed.order()).isEqualTo(2);
		assertThat(em.find(Place.class, placeId).getName()).isEqualTo("경포대");
	}

	/** 059 응답을 그대로 돌려보낸 요청이 새 장소 행을 만들거나 완성을 풀지 않는다(PR #77 리뷰). */
	@Test
	void 같은_직접_입력_장소를_다시_보내면_장소_행도_version도_그대로다() {
		em.find(TravelRecord.class, recordId).complete(NOW);
		em.flush();
		long placesBefore = placeCount();
		VisitPlaceInput.CustomPlace same = new VisitPlaceInput.CustomPlace("경포대", new BigDecimal("37.7951000"),
			new BigDecimal("128.9087000"), null);

		VisitUpdateResponse response = update(2,
			List.of(new VisitUpdateCommand.Item(b.getId(), same, DAY1, AT1, null)), List.of(), false);

		assertThat(response.version()).isEqualTo(2);
		assertThat(response.status()).isEqualTo(RecordStatus.COMPLETED);
		assertThat(placeCount()).isEqualTo(placesBefore);
	}

	@Test
	void 장소를_바꾸거나_방문을_지워_아무도_쓰지_않게_된_직접_입력_장소는_지운다() {
		Long ownA = ownPlace(a);
		Long ownC = ownPlace(c);
		VisitPlaceInput.CustomPlace other = new VisitPlaceInput.CustomPlace("안목 카페", new BigDecimal("37.7720000"),
			new BigDecimal("128.9470000"), null);

		update(1, List.of(new VisitUpdateCommand.Item(a.getId(), other, DAY1, AT1, null)), List.of(c.getId()), false);

		assertThat(em.find(Place.class, ownA)).isNull();
		assertThat(em.find(Place.class, ownC)).isNull();
		assertThat(em.find(Place.class, placeId)).isNotNull();
	}

	@Test
	void 다른_방문이나_여행방_후보가_쓰는_장소는_지우지_않는다() {
		Long roomId = em.find(TravelRecord.class, recordId).getRoomId();
		Long candidate = ownPlace(a);
		em.persist(RoomPlace.manual(roomId, em.find(Place.class, candidate), MEMBER, NOW));
		em.flush();

		update(1, List.of(), List.of(a.getId(), b.getId()), false);

		assertThat(em.find(Place.class, candidate)).isNotNull();
		assertThat(em.find(Place.class, placeId)).isNotNull();
	}

	@Test
	void 순서를_주면_그_자리로_옮기고_나머지를_민다() {
		update(1, List.of(keep(c, 1)), List.of(), false);

		assertThat(layout()).containsExactly(DAY1 + ":1:" + c.getId(), DAY1 + ":2:" + a.getId(),
			DAY1 + ":3:" + b.getId());
		assertThat(version()).isEqualTo(2);
	}

	@Test
	void 순서_없이_날짜만_바꾸면_새_날짜_맨_뒤에_붙고_원래_날짜는_당겨진다() {
		ActualVisit x = visit(DAY2, 1);
		em.flush();

		update(1, List.of(new VisitUpdateCommand.Item(a.getId(), null, DAY2, AT2, null)), List.of(), false);

		assertThat(layout()).containsExactly(DAY1 + ":1:" + b.getId(), DAY1 + ":2:" + c.getId(),
			DAY2 + ":1:" + x.getId(), DAY2 + ":2:" + a.getId());
	}

	@Test
	void 사진_이야기가_없는_방문은_확인_없이_지우고_남은_순서를_당긴다() {
		VisitUpdateResponse response = update(1, List.of(), List.of(b.getId()), false);

		assertThat(layout()).containsExactly(DAY1 + ":1:" + a.getId(), DAY1 + ":2:" + c.getId());
		assertThat(response.actualVisits()).hasSize(2);
		assertThat(response.version()).isEqualTo(2);
	}

	@Test
	void 지울_방문에_사진이_하나라도_있으면_확인값과_상관없이_RE020이고_다른_수정도_반영하지_않는다() {
		em.persist(VisitImage.unlinked(recordId, FRIEND, "record-photo/a", NOW));
		em.flush();
		em.getEntityManager().createNativeQuery("update visit_images set visit_id = ?1, position = 1")
			.setParameter(1, b.getId()).executeUpdate();

		assertError(() -> update(1, List.of(keep(c, 1)), List.of(b.getId()), true), ExceptionType.VISIT_HAS_PHOTOS);
		assertThat(layout()).hasSize(3).first().isEqualTo(DAY1 + ":1:" + a.getId());
	}

	@Test
	void 이야기가_있으면_확인이_없을_때_RE023_확인하면_다른_사람_이야기까지_지운다() {
		Long storyId = em.persist(VisitStory.write(b.getId(), FRIEND, "좋았다", NOW)).getId();
		em.flush();

		assertError(() -> update(1, List.of(), List.of(b.getId()), false), ExceptionType.VISIT_STORIES_NOT_CONFIRMED);

		update(1, List.of(), List.of(b.getId()), true);
		assertThat(em.find(VisitStory.class, storyId)).isNull();
		assertThat(em.find(ActualVisit.class, b.getId())).isNull();
	}

	@Test
	void 이_기록의_방문이_아니면_RE013() {
		assertError(() -> update(1, List.of(new VisitUpdateCommand.Item(b.getId() + 100, null, DAY1, AT1, null)),
			List.of(), false), ExceptionType.VISIT_NOT_FOUND);
		assertError(() -> update(1, List.of(), List.of(b.getId() + 100), false), ExceptionType.VISIT_NOT_FOUND);
	}

	@Test
	void version이_다르면_C004() {
		assertError(() -> update(2, List.of(keep(c, 1)), List.of(), false), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 바뀐_것이_없으면_version과_완성_상태를_그대로_둔다() {
		em.find(TravelRecord.class, recordId).complete(NOW);
		em.flush();

		VisitUpdateResponse response = update(2, List.of(keep(a, 1), keep(b, null)), List.of(), false);

		assertThat(response.version()).isEqualTo(2);
		assertThat(response.status()).isEqualTo(RecordStatus.COMPLETED);
	}

	@Test
	void 완성한_기록을_고치면_DRAFT로_돌아간다() {
		em.find(TravelRecord.class, recordId).complete(NOW);
		em.flush();

		VisitUpdateResponse response = update(2, List.of(keep(c, 1)), List.of(), false);

		assertThat(response.status()).isEqualTo(RecordStatus.DRAFT);
		assertThat(response.version()).isEqualTo(3);
	}

	@Test
	void 나간_사람은_RE003() {
		assertError(() -> visitCommandService.update(LEFT, recordId, new VisitUpdateCommand(1, List.of(keep(c, 1)),
			List.of(), false)), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	/** Flyway V1 의 DEFERRABLE INITIALLY IMMEDIATE 유니크(문장마다 검사)를 걸어도 옮기고 지울 수 있다. */
	@Test
	void 날짜_안_순서_유니크가_있어도_옮기고_지운다() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assumeTrue("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()),
				"PostgreSQL 에서만 확인한다");
		}
		em.getEntityManager().createNativeQuery("alter table actual_visits add constraint uq_test_visit_position"
			+ " unique (record_id, visit_date, position) deferrable initially immediate").executeUpdate();

		update(1, List.of(keep(c, 1)), List.of(a.getId()), false);

		assertThat(layout()).containsExactly(DAY1 + ":1:" + c.getId(), DAY1 + ":2:" + b.getId());
	}

	/* ── 참고 계획 연결(명세 specs/record-plan.md 3-3) ───────────── */

	private static VisitUpdateCommand.Item plan(ActualVisit visit, Long plannedItemId) {
		return new VisitUpdateCommand.Item(visit.getId(), null, visit.getVisitDate(), visit.getVisitedAt(), null, true,
			plannedItemId);
	}

	private Long plannedItemOf(ActualVisit visit) {
		return em.find(ActualVisit.class, visit.getId()).getPlannedItemId();
	}

	@Test
	void 값을_보내면_연결하고_생략하면_유지하고_null_이면_해제한다() {
		VisitUpdateResponse linked = update(1, List.of(plan(a, 501L), plan(b, 502L)), List.of(), false);

		assertThat(linked.version()).isEqualTo(2);
		assertThat(linked.actualVisits()).extracting(ActualVisitView::plannedItemId).containsExactly(501L, 502L, null);

		VisitUpdateResponse changed = update(2, List.of(keep(a, null), plan(b, null)), List.of(), false);

		assertThat(changed.version()).isEqualTo(3);
		assertThat(plannedItemOf(a)).isEqualTo(501L);
		assertThat(plannedItemOf(b)).isNull();
	}

	@Test
	void 같은_항목을_다시_보내면_바뀐_것이_없어_version_그대로다() {
		update(1, List.of(plan(a, 501L)), List.of(), false);
		em.find(TravelRecord.class, recordId).complete(NOW);
		em.flush();

		VisitUpdateResponse response = update(3, List.of(plan(a, 501L)), List.of(), false);

		assertThat(response.version()).isEqualTo(3);
		assertThat(response.status()).isEqualTo(RecordStatus.COMPLETED);
	}

	@Test
	void 사본에_없는_항목이면_C003이고_아무것도_바꾸지_않는다() {
		assertError(() -> update(1, List.of(keep(c, 1), plan(a, 999L)), List.of(), false),
			ExceptionType.NOT_ALLOWED_VALUE);

		assertThat(plannedItemOf(a)).isNull();
		assertThat(layout()).containsExactly(DAY1 + ":1:" + a.getId(), DAY1 + ":2:" + b.getId(),
			DAY1 + ":3:" + c.getId());
	}
}
