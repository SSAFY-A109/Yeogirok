package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.PhotoLinkResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.TestUsers;

/** API-068 방문에 사진 연결·이동. */
@DataJpaTest
@Import(RecordTestConfig.class)
class VisitPhotoServiceTest {

	static final Long ME = 9201L;
	static final Long WITHDRAWN = 9202L;
	static final Long LEFT = 9203L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");

	@Autowired
	VisitPhotoService visitPhotoService;
	@Autowired
	TestEntityManager em;
	@Autowired
	DataSource dataSource;

	Long recordId;
	Long visitA;
	Long visitB;
	Long otherRecordId;
	int uploads;

	@BeforeEach
	void setUp() {
		TestUsers.insertActive(em.getEntityManager(), ME, LEFT);
		TestUsers.insert(em.getEntityManager(), WITHDRAWN, "WITHDRAWN");
		recordId = record(true);
		visitA = visit(recordId, 1);
		visitB = visit(recordId, 2);
		otherRecordId = record(false);
		em.flush();
	}

	private Long record(boolean joined) {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY1)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		if (joined) {
			em.persist(RoomMember.creator(room.getId(), ME, NOW));
			em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		}
		return em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
	}

	private Long visit(Long recordId, int position) {
		ActualVisit visit = ActualVisit.manual(recordId, 1L, DAY1, NOW, NOW);
		visit.moveTo(position, NOW);
		return em.persist(visit).getId();
	}

	private Long image(Long recordId, Long uploadedBy) {
		return em.persist(VisitImage.unlinked(recordId, uploadedBy, "record-photo/" + (++uploads), NOW)).getId();
	}

	private PhotoLinkResponse link(Long visitId, Long... imageIds) {
		PhotoLinkResponse response = visitPhotoService.link(ME, recordId, visitId, List.of(imageIds));
		em.flush();
		em.clear();
		return response;
	}

	/** @return 방문 안 순서대로 사진 ID */
	private List<Long> photosOf(Long visitId) {
		em.flush();
		em.clear();
		return em.getEntityManager().createQuery("select i.id from VisitImage i where i.visitId = :visitId"
			+ " order by i.position", Long.class).setParameter("visitId", visitId).getResultList();
	}

	private int version() {
		return em.find(TravelRecord.class, recordId).getVersion();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 보관함_사진을_보낸_순서대로_방문_맨_뒤에_붙이고_탈퇴자_업로더는_null이다() {
		Long first = image(recordId, ME);
		Long second = image(recordId, WITHDRAWN);

		PhotoLinkResponse response = link(visitA, second, first);

		assertThat(response.photos()).containsExactly(new PhotoLinkResponse.Photo(second, visitA, null),
			new PhotoLinkResponse.Photo(first, visitA, ME));
		assertThat(response.version()).isEqualTo(2);
		assertThat(response.status()).isEqualTo(RecordStatus.DRAFT);
		assertThat(photosOf(visitA)).containsExactly(second, first);
	}

	@Test
	void 다른_방문으로_옮기면_원래_방문은_당겨지고_공개_선택은_FALSE로_돌아간다() {
		Long p1 = image(recordId, ME);
		Long p2 = image(recordId, ME);
		Long p3 = image(recordId, ME);
		Long q1 = image(recordId, ME);
		link(visitA, p1, p2, p3);
		link(visitB, q1);
		em.getEntityManager().createNativeQuery("update visit_images set is_public_selected = TRUE where id = ?1")
			.setParameter(1, p2).executeUpdate();

		link(visitB, p2);

		assertThat(photosOf(visitA)).containsExactly(p1, p3);
		assertThat(photosOf(visitB)).containsExactly(q1, p2);
		assertThat(em.find(VisitImage.class, p2).isPublicSelected()).isFalse();
		assertThat(version()).isEqualTo(4);
	}

	@Test
	void 이미_그_방문에_있는_사진이나_빈_배열은_바꾸지_않는다() {
		Long p1 = image(recordId, ME);
		link(visitA, p1);

		PhotoLinkResponse again = link(visitA, p1);
		PhotoLinkResponse empty = link(visitA);

		assertThat(again.version()).isEqualTo(2);
		assertThat(again.photos()).containsExactly(new PhotoLinkResponse.Photo(p1, visitA, ME));
		assertThat(empty.photos()).isEmpty();
		assertThat(empty.version()).isEqualTo(2);
	}

	@Test
	void 다른_기록_사진이나_없는_사진은_M004이고_아무것도_옮기지_않는다() {
		Long mine = image(recordId, ME);
		Long others = image(otherRecordId, ME);

		assertError(() -> link(visitA, mine, others), ExceptionType.FILE_NOT_FOUND);
		assertError(() -> link(visitA, mine, mine + 1000), ExceptionType.FILE_NOT_FOUND);
		assertThat(photosOf(visitA)).isEmpty();
	}

	@Test
	void 다른_기록의_방문은_RE013_나간_사람은_RE003() {
		Long mine = image(recordId, ME);
		Long otherVisit = visit(otherRecordId, 1);

		assertError(() -> link(otherVisit, mine), ExceptionType.VISIT_NOT_FOUND);
		assertError(() -> visitPhotoService.link(LEFT, recordId, visitA, List.of(mine)),
			ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	@Test
	void 멱등_재전송은_그_사진들의_지금_연결을_준다() {
		Long p1 = image(recordId, ME);
		link(visitA, p1);
		link(visitB, p1);

		PhotoLinkResponse replay = visitPhotoService.getLinked(ME, recordId, List.of(p1));

		assertThat(replay.photos()).containsExactly(new PhotoLinkResponse.Photo(p1, visitB, ME));
		assertThat(replay.version()).isEqualTo(3);
	}

	/** Flyway V1 의 DEFERRABLE INITIALLY IMMEDIATE 유니크(문장마다 검사)를 걸어도 옮기고 당길 수 있다. */
	@Test
	void 방문_안_사진_순서_유니크가_있어도_옮긴다() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assumeTrue("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()),
				"PostgreSQL 에서만 확인한다");
		}
		em.getEntityManager().createNativeQuery("alter table visit_images add constraint uq_test_image_position"
			+ " unique (visit_id, position) deferrable initially immediate").executeUpdate();
		Long p1 = image(recordId, ME);
		Long p2 = image(recordId, ME);
		Long p3 = image(recordId, ME);
		Long q1 = image(recordId, ME);
		link(visitA, p1, p2, p3);
		link(visitB, q1);

		link(visitB, p1);

		assertThat(photosOf(visitA)).containsExactly(p2, p3);
		assertThat(photosOf(visitB)).containsExactly(q1, p1);
	}
}
