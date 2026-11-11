package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.CourseDay;
import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CoursePreviewResponse;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.TestUsers;

/** 179 API-072 미리보기. */
@DataJpaTest
@Import({CourseTestConfig.class, CoursePreviewService.class})
class CoursePreviewServiceTest {

	static final long ME = 9401L;
	static final long OTHER = 9402L;
	static final long STRANGER = 9409L;

	@Autowired
	CoursePreviewService coursePreviewService;
	@Autowired
	TestEntityManager em;

	TravelRoom room;
	Long recordId;
	ActualVisit visit;
	VisitImage photo;

	@BeforeEach
	void setUp() {
		CourseFixture fixture = new CourseFixture(em);
		room = fixture.room(DAY1, ME, OTHER);
		TestUsers.insertActive(em.getEntityManager(), STRANGER);
		recordId = fixture.record(room).getId();
		visit = fixture.visit(recordId, fixture.place("경포대"), DAY1, 1);
		photo = fixture.photo(recordId, visit.getId(), 1);
		fixture.photo(recordId, null, 0);
		em.flush();
		em.clear();
	}

	private CoursePreviewResponse preview(long userId, int version, String description, boolean includePhotos) {
		return coursePreviewService.preview(userId, recordId,
			new CourseInput(version, "강릉 1박", description, includePhotos, null, null));
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 사진_포함_미리보기는_게시될_모양과_해시를_준다() {
		CoursePreviewResponse response = preview(ME, 1, "바다 여행", true);

		assertThat(response.preview().title()).isEqualTo("강릉 1박");
		assertThat(response.preview().description()).isEqualTo("바다 여행");
		assertThat(response.preview().authors()).extracting(PublicAuthor::id).containsExactly(ME, OTHER);
		CourseDay.Item item = response.preview().days().getFirst().items().getFirst();
		assertThat(item.visitId()).isEqualTo(visit.getId());
		assertThat(item.photos()).hasSize(1).first().extracting("imageId").isEqualTo(photo.getId());
		assertThat(response.selectionHash()).hasSize(64);
		assertThat(response.excluded()).isEmpty();
		assertThat(response.warnings()).isEmpty();
	}

	@Test
	void 사진을_빼면_photos가_비고_해시도_다르다() {
		CoursePreviewResponse without = preview(ME, 1, "", false);

		assertThat(without.preview().days().getFirst().items().getFirst().photos()).isEmpty();
		assertThat(without.selectionHash()).isNotEqualTo(preview(ME, 1, "", true).selectionHash());
	}

	@Test
	void 설명을_생략하면_지금_기록_설명을_빈_문자열이면_비운_설명을_쓴다() {
		em.find(TravelRecord.class, recordId).changeCourse("강릉 여행", "원래 설명", null, false, NOW);
		em.flush();
		em.clear();

		assertThat(preview(ME, 2, null, false).preview().description()).isEqualTo("원래 설명");
		assertThat(preview(ME, 2, "", false).preview().description()).isEmpty();
		assertThat(preview(ME, 2, null, false).selectionHash()).isNotEqualTo(preview(ME, 2, "", false).selectionHash());
	}

	@Test
	void 아무것도_저장하지_않는다() {
		preview(ME, 1, "새 설명", true);
		em.clear();

		TravelRecord record = em.find(TravelRecord.class, recordId);
		assertThat(record.getTitle()).isEqualTo("강릉 여행");
		assertThat(record.getDescription()).isEmpty();
		assertThat(record.getVersion()).isEqualTo(1);
		assertThat(record.getPublishedAt()).isNull();
		assertThat(em.find(VisitImage.class, photo.getId()).isPublicSelected()).isFalse();
	}

	@Test
	void 완성하지_않은_기록도_볼_수_있다() {
		assertThat(em.find(TravelRecord.class, recordId).isCompleted()).isFalse();

		assertThat(preview(ME, 1, "", true).preview().days()).hasSize(1);
	}

	@Test
	void version이_다르면_C004() {
		assertError(() -> preview(ME, 2, "", true), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 참여자가_아니거나_방이_삭제됐으면_RE003_1차_쓰기와_같다() {
		assertError(() -> preview(STRANGER, 1, "", true), ExceptionType.RECORD_EDIT_FORBIDDEN);

		em.find(TravelRoom.class, room.getId()).softDelete(NOW);
		em.flush();
		assertError(() -> preview(ME, 1, "", true), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	@Test
	void 없는_기록은_RE001() {
		assertError(() -> coursePreviewService.preview(ME, -1L, new CourseInput(1, "t", null, true, null, null)),
			ExceptionType.RECORD_NOT_FOUND);
	}
}
