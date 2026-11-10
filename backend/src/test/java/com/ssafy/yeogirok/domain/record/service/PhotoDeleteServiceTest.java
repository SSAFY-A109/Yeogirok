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
import com.ssafy.yeogirok.domain.record.dto.PhotoDeleteResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.entity.VisitStory;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** API-089 기록 사진 삭제. 커밋 뒤 파일 삭제는 RecordPhotoDeleteCommitTest 가 본다. */
@DataJpaTest
@Import(RecordTestConfig.class)
class PhotoDeleteServiceTest {

	static final Long ME = 9401L;
	static final Long FRIEND = 9402L;
	static final Long LEFT = 9403L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");

	@Autowired
	VisitPhotoService visitPhotoService;
	@Autowired
	TestEntityManager em;
	@Autowired
	DataSource dataSource;

	Long recordId;
	Long visitId;
	int uploads;

	@BeforeEach
	void setUp() {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY1)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		recordId = em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
		ActualVisit visit = ActualVisit.manual(recordId, 1L, DAY1, NOW, NOW);
		visit.moveTo(1, NOW);
		visitId = em.persist(visit).getId();
		em.flush();
	}

	/** @param position null 이면 보관함(미연결) 사진 */
	private Long image(Long recordId, Integer position) {
		Long id = em.persist(VisitImage.unlinked(recordId, FRIEND, "record-photo/" + (++uploads), NOW)).getId();
		if (position != null) {
			em.find(VisitImage.class, id).linkTo(visitId, position);
		}
		em.flush();
		return id;
	}

	/** @return 방문 안 순서대로 "순서:id" */
	private List<String> photos() {
		em.flush();
		em.clear();
		return em.getEntityManager().createQuery("select i from VisitImage i where i.visitId = :visitId"
				+ " order by i.position", VisitImage.class).setParameter("visitId", visitId).getResultList().stream()
			.map(i -> i.getPosition() + ":" + i.getId()).toList();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 업로더가_아니어도_지울_수_있고_남은_사진_순서를_당기고_기록_version이_오른다() {
		Long p1 = image(recordId, 1);
		Long p2 = image(recordId, 2);
		Long p3 = image(recordId, 3);
		em.find(TravelRecord.class, recordId).complete(NOW);
		em.flush();

		PhotoDeleteResponse response = visitPhotoService.delete(ME, recordId, p2, 2);

		assertThat(response).isEqualTo(new PhotoDeleteResponse(recordId, p2, true, RecordStatus.DRAFT, 3));
		assertThat(photos()).containsExactly("1:" + p1, "2:" + p3);
		assertThat(em.find(VisitImage.class, p2)).isNull();
	}

	@Test
	void 보관함_사진도_지우고_이야기는_건드리지_않는다() {
		Long pool = image(recordId, null);
		Long storyId = em.persistAndGetId(VisitStory.write(visitId, FRIEND, "좋았다", NOW), Long.class);

		visitPhotoService.delete(ME, recordId, pool, 1);

		em.flush();
		em.clear();
		assertThat(em.find(VisitImage.class, pool)).isNull();
		assertThat(em.find(VisitStory.class, storyId)).isNotNull();
	}

	@Test
	void 다른_기록_사진이나_없는_사진은_M004() {
		Long otherRecordId = em.persist(TravelRecord.create(recordId + 1000, "다른 기록", NOW)).getId();
		Long others = image(otherRecordId, null);

		assertError(() -> visitPhotoService.delete(ME, recordId, others, 1), ExceptionType.FILE_NOT_FOUND);
		assertError(() -> visitPhotoService.delete(ME, recordId, others + 1000, 1), ExceptionType.FILE_NOT_FOUND);
	}

	@Test
	void version이_다르면_C004_나간_사람은_RE003_없는_기록은_RE001() {
		Long p1 = image(recordId, 1);

		assertError(() -> visitPhotoService.delete(ME, recordId, p1, 2), ExceptionType.VERSION_MISMATCH);
		assertError(() -> visitPhotoService.delete(LEFT, recordId, p1, 1), ExceptionType.RECORD_EDIT_FORBIDDEN);
		assertError(() -> visitPhotoService.delete(ME, recordId + 1000, p1, 1), ExceptionType.RECORD_NOT_FOUND);
		assertThat(photos()).containsExactly("1:" + p1);
	}

	@Test
	void 멱등_재전송은_지운_뒤에도_같은_성공을_주고_version을_다시_올리지_않는다() {
		Long p1 = image(recordId, 1);
		visitPhotoService.delete(ME, recordId, p1, 1);
		em.flush();

		PhotoDeleteResponse again = visitPhotoService.getDeleted(ME, recordId, p1);

		assertThat(again).isEqualTo(new PhotoDeleteResponse(recordId, p1, true, RecordStatus.DRAFT, 2));
		assertError(() -> visitPhotoService.getDeleted(LEFT, recordId, p1), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	/** Flyway V1 의 DEFERRABLE INITIALLY IMMEDIATE 유니크(문장마다 검사)를 걸어도 남은 사진을 당길 수 있다. */
	@Test
	void 방문_안_사진_순서_유니크가_있어도_당긴다() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assumeTrue("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()),
				"PostgreSQL 에서만 확인한다");
		}
		em.getEntityManager().createNativeQuery("alter table visit_images add constraint uq_test_image_position"
			+ " unique (visit_id, position) deferrable initially immediate").executeUpdate();
		Long p1 = image(recordId, 1);
		Long p2 = image(recordId, 2);
		Long p3 = image(recordId, 3);
		Long p4 = image(recordId, 4);

		visitPhotoService.delete(ME, recordId, p1, 1);

		assertThat(photos()).containsExactly("1:" + p2, "2:" + p3, "3:" + p4);
	}
}
