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
import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CoursePublicationResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 180 API-073 게시. */
@DataJpaTest
@Import({CourseTestConfig.class, CoursePreviewService.class, CoursePublishService.class})
class CoursePublishServiceTest {

	static final long ME = 9501L;
	static final long STRANGER = 9509L;

	@Autowired
	CoursePreviewService coursePreviewService;
	@Autowired
	CoursePublishService coursePublishService;
	@Autowired
	RecordQueryService recordQueryService;
	@Autowired
	TestEntityManager em;

	CourseFixture fixture;
	Long recordId;
	ActualVisit visit;
	VisitImage linked;
	VisitImage unlinked;

	@BeforeEach
	void setUp() {
		fixture = new CourseFixture(em);
		TravelRecord record = fixture.record(fixture.room(DAY1, ME));
		recordId = record.getId();
		visit = fixture.visit(recordId, fixture.place("경포대"), DAY1, 1);
		linked = fixture.photo(recordId, visit.getId(), 1);
		unlinked = fixture.photo(recordId, null, 0);
		record.complete(NOW);
		em.flush();
		em.clear();
	}

	/** 지금 상태로 미리보기를 받아 그 해시로 게시 요청을 만든다. */
	private CourseInput confirmed(String description, boolean includePhotos) {
		int version = em.find(TravelRecord.class, recordId).getVersion();
		em.clear();
		String hash = coursePreviewService.preview(ME, recordId,
			new CourseInput(version, "강릉 1박", description, includePhotos, null, null)).selectionHash();
		return new CourseInput(version, "강릉 1박", description, includePhotos, hash, null);
	}

	private CoursePublicationResponse publish(CourseInput input) {
		CoursePublicationResponse response = coursePublishService.publish(ME, recordId, input);
		em.flush();
		em.clear();
		return response;
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 게시하면_제목_설명_게시시각을_저장하고_version이_오르고_완성_상태는_그대로() {
		CoursePublicationResponse response = publish(confirmed("바다", true));

		assertThat(response).isEqualTo(new CoursePublicationResponse(recordId, NOW, 3));
		TravelRecord record = em.find(TravelRecord.class, recordId);
		assertThat(record.getTitle()).isEqualTo("강릉 1박");
		assertThat(record.getDescription()).isEqualTo("바다");
		assertThat(record.getStatus()).isEqualTo(RecordStatus.COMPLETED);
		assertThat(recordQueryService.getDetail(ME, recordId).publishedCourseId()).isEqualTo(recordId);
		assertThat(recordQueryService.getList(ME, null, null, null, null).items().getFirst().publication())
			.extracting("courseId", "version").containsExactly(recordId, 3);
	}

	@Test
	void 사진_포함이면_연결된_사진만_공개되고_미연결은_비공개() {
		publish(confirmed("", true));

		assertThat(em.find(VisitImage.class, linked.getId()).isPublicSelected()).isTrue();
		assertThat(em.find(VisitImage.class, unlinked.getId()).isPublicSelected()).isFalse();
		assertThat(em.find(ActualVisit.class, visit.getId()).isPublicSelected()).isTrue();
	}

	@Test
	void 사진_제외면_모든_사진이_비공개() {
		publish(confirmed("", false));

		assertThat(em.find(VisitImage.class, linked.getId()).isPublicSelected()).isFalse();
	}

	@Test
	void 설명을_생략하면_지금_설명을_그대로_둔다() {
		publish(confirmed(null, false));

		assertThat(em.find(TravelRecord.class, recordId).getDescription()).isEmpty();
	}

	@Test
	void 미리보기_뒤_사진을_연결하면_CO010() {
		CourseInput input = confirmed("", true);
		fixture.photo(recordId, visit.getId(), 2);
		em.flush();
		em.clear();

		assertError(() -> publish(input), ExceptionType.COURSE_SELECTION_CHANGED);
	}

	@Test
	void 미리보기와_다른_제목이나_version이면_CO010() {
		CourseInput input = confirmed("", true);

		assertError(() -> publish(new CourseInput(input.version(), "다른 제목", "", true, input.selectionHash(), null)),
			ExceptionType.COURSE_SELECTION_CHANGED);
		assertError(() -> publish(new CourseInput(input.version() + 1, "강릉 1박", "", true, input.selectionHash(), null)),
			ExceptionType.COURSE_SELECTION_CHANGED);
	}

	@Test
	void 이미_게시_중이면_CO011() {
		publish(confirmed("", true));

		assertError(() -> publish(confirmed("", true)), ExceptionType.COURSE_ALREADY_PUBLISHED);
	}

	@Test
	void 완성하지_않은_기록은_RE019() {
		em.find(TravelRecord.class, recordId).markEdited(NOW);
		em.flush();
		em.clear();

		assertError(() -> publish(confirmed("", true)), ExceptionType.RECORD_NOT_PUBLISHABLE);
	}

	@Test
	void 참여자가_아니면_RE003() {
		CourseInput input = confirmed("", true);

		assertError(() -> coursePublishService.publish(STRANGER, recordId, input), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	@Test
	void 재전송_응답은_지금_기록_값() {
		CoursePublicationResponse first = publish(confirmed("", true));

		assertThat(coursePublishService.getPublished(ME, recordId)).isEqualTo(first);
		assertError(() -> coursePublishService.getPublished(STRANGER, recordId), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}
}
