package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CoursePublicationResponse;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;

/** 182 API-075 수정·재게시·공개 해제. 요청 형식 오류는 CourseControllerTest 가 본다. */
@DataJpaTest
@Import({CourseTestConfig.class, CoursePreviewService.class, CoursePublishService.class, CourseUpdateService.class,
	MyCourseQueryService.class})
class CourseUpdateServiceTest {

	static final long ME = 9701L;
	static final long STRANGER = 9709L;

	@Autowired
	CoursePreviewService coursePreviewService;
	@Autowired
	CoursePublishService coursePublishService;
	@Autowired
	CourseUpdateService courseUpdateService;
	@Autowired
	MyCourseQueryService myCourseQueryService;
	@Autowired
	RecordQueryService recordQueryService;
	@Autowired
	MutableClock clock;
	@Autowired
	TestEntityManager em;

	Long courseId;
	Long roomId;
	VisitImage linked;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		CourseFixture fixture = new CourseFixture(em);
		fixture.room(DAY1, STRANGER);
		TravelRoom room = fixture.room(DAY1, ME);
		roomId = room.getId();
		TravelRecord record = fixture.record(room);
		courseId = record.getId();
		Long visitId = fixture.visit(courseId, fixture.place("경포대"), DAY1, 1).getId();
		linked = fixture.photo(courseId, visitId, 1);
		record.complete(NOW);
		em.flush();
		em.clear();
		coursePublishService.publish(ME, courseId, new CourseInput(2, "강릉 1박", "", true, hash("강릉 1박", "", true), null));
		flush();
	}

	private TravelRecord record() {
		return em.find(TravelRecord.class, courseId);
	}

	/** 지금 기록 version 으로 미리보기(072)를 받아 해시를 얻는다. */
	private String hash(String title, String description, boolean includePhotos) {
		int version = record().getVersion();
		em.clear();
		return coursePreviewService.preview(ME, courseId,
			new CourseInput(version, title, description, includePhotos, null, null)).selectionHash();
	}

	private CoursePublicationResponse update(String title, Boolean includePhotos, String hash, Boolean published) {
		int version = record().getVersion();
		em.clear();
		CoursePublicationResponse response = courseUpdateService.update(ME, courseId,
			new CourseInput(version, title, null, includePhotos, hash, published));
		flush();
		return response;
	}

	private void flush() {
		em.flush();
		em.clear();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 해제하면_게시시각이_없어지고_목록에서_빠지며_해시가_필요_없다() {
		CoursePublicationResponse response = update(null, null, null, false);

		assertThat(response).isEqualTo(new CoursePublicationResponse(courseId, null, 4));
		assertThat(myCourseQueryService.getMine(ME, null, null).items()).isEmpty();
		assertThat(recordQueryService.getList(ME, null, null, null, null).items().getFirst().publication()).isNull();
		assertThat(record().getStatus()).isEqualTo(RecordStatus.COMPLETED);
	}

	@Test
	void 해제_뒤_새_해시로_다시_게시하면_새_게시시각() {
		// 해제해도 사진 공개 선택은 남아, includePhotos 를 생략하면 사진 포함으로 해시를 맞춘다.
		update(null, null, null, false);
		clock.advance(Duration.ofHours(1));

		CoursePublicationResponse response = update(null, null, hash("강릉 1박", "", true), true);

		assertThat(response.publishedAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
		assertThat(response.version()).isEqualTo(5);
	}

	@Test
	void 완성하지_않은_기록은_다시_게시할_수_없다_RE019() {
		update(null, null, null, false);
		record().markEdited(NOW);
		flush();

		assertError(() -> update(null, null, hash("강릉 1박", "", true), true), ExceptionType.RECORD_NOT_PUBLISHABLE);
	}

	@Test
	void 제목만_바꾸면_해시가_필요하고_게시시각은_그대로() {
		assertError(() -> update("새 제목", null, null, null), ExceptionType.REQUIRED_VALUE_MISSING);
		assertError(() -> update("새 제목", null, hash("강릉 1박", "", true), null), ExceptionType.COURSE_SELECTION_CHANGED);

		// includePhotos 를 생략하면 지금 사진 공개 여부(true)로 해시를 맞춘다.
		CoursePublicationResponse response = update("새 제목", null, hash("새 제목", "", true), null);

		assertThat(response).isEqualTo(new CoursePublicationResponse(courseId, NOW, 4));
		assertThat(record().getTitle()).isEqualTo("새 제목");
		assertThat(em.find(VisitImage.class, linked.getId()).isPublicSelected()).isTrue();
	}

	@Test
	void 사진_공개를_끄면_모든_사진이_비공개() {
		update(null, false, hash("강릉 1박", "", false), null);

		assertThat(em.find(VisitImage.class, linked.getId()).isPublicSelected()).isFalse();
	}

	@Test
	void 바꾼_것이_없으면_version_그대로() {
		assertThat(update(null, null, null, true).version()).isEqualTo(3);
		assertThat(update(null, null, null, null).version()).isEqualTo(3);
		assertThat(update("강릉 1박", true, hash("강릉 1박", "", true), null).version()).isEqualTo(3);
	}

	@Test
	void 없는_코스_삭제된_방은_CO013_비참여는_CO008_version_다르면_C004() {
		CourseInput unpublish = new CourseInput(3, null, null, null, null, false);

		assertError(() -> courseUpdateService.update(ME, courseId + 1000, unpublish), ExceptionType.COURSE_NOT_FOUND);
		assertError(() -> courseUpdateService.update(STRANGER, courseId, unpublish), ExceptionType.COURSE_NOT_MEMBER);
		assertError(() -> courseUpdateService.update(ME, courseId, new CourseInput(2, null, null, null, null, false)),
			ExceptionType.VERSION_MISMATCH);
		em.find(TravelRoom.class, roomId).softDelete(NOW);
		flush();
		assertError(() -> courseUpdateService.update(ME, courseId, unpublish), ExceptionType.COURSE_NOT_FOUND);
	}

	@Test
	void 게시_중인_기록을_1차_API로_편집해_DRAFT가_돼도_게시시각은_남는다() {
		record().markEdited(NOW);
		flush();

		assertThat(record().getStatus()).isEqualTo(RecordStatus.DRAFT);
		assertThat(record().getPublishedAt()).isEqualTo(NOW);
	}
}
