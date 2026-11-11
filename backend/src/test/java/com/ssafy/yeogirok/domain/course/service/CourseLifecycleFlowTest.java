package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.CourseDetailResponse;
import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchCondition;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 189 코스 공개 → 검색 → 상세 → 해제 흐름. 072·073·075 로 바꾼 상태가 014·016 에 그대로 보이는지 서비스를 이어 확인한다.
 */
@DataJpaTest
@Import({CourseTestConfig.class, CoursePreviewService.class, CoursePublishService.class, CourseUpdateService.class,
	CourseSearchService.class, CourseDetailService.class})
class CourseLifecycleFlowTest {

	static final long ME = 9951L;
	static final CourseSearchCondition ALL = CourseSearchCondition.of(null, null, null, null, null, null);

	@Autowired
	CoursePreviewService previewService;
	@Autowired
	CoursePublishService publishService;
	@Autowired
	CourseUpdateService updateService;
	@Autowired
	CourseSearchService searchService;
	@Autowired
	CourseDetailService detailService;
	@Autowired
	TestEntityManager em;

	private int version(Long recordId) {
		int version = em.find(TravelRecord.class, recordId).getVersion();
		em.clear();
		return version;
	}

	private String previewHash(Long recordId, String title) {
		return previewService.preview(ME, recordId, new CourseInput(version(recordId), title, "바다", true, null, null))
			.selectionHash();
	}

	private void flush() {
		em.flush();
		em.clear();
	}

	@Test
	void 게시하면_검색과_상세에_나오고_해제하면_검색에서_빠지고_상세는_CO001() {
		CourseFixture fixture = new CourseFixture(em);
		TravelRecord record = fixture.record(fixture.room(DAY1, ME));
		Long courseId = record.getId();
		Long visitId = fixture.visit(courseId, fixture.place("경포대"), DAY1, 1).getId();
		Long photoId = fixture.photo(courseId, visitId, 1).getId();
		record.complete(NOW);
		flush();

		publishService.publish(ME, courseId,
			new CourseInput(version(courseId), "강릉 1박", "바다", true, previewHash(courseId, "강릉 1박"), null));
		flush();

		CourseSearchResponse found = searchService.search(CourseSearchCondition.of("경포", null, null, null, null, null),
			null, null);
		assertThat(found.items()).extracting(CourseSearchResponse.Item::id).containsExactly(courseId);
		assertThat(found.items().getFirst().title()).isEqualTo("강릉 1박");
		CourseDetailResponse detail = detailService.getDetail(courseId);
		assertThat(detail.days().getFirst().items().getFirst().photos()).extracting("imageId").containsExactly(photoId);
		assertThat(detail.version()).isEqualTo(version(courseId));

		// 게시한 뒤 제목을 바꾸면 검색·상세에 바로 보인다(공개 사본이 없다).
		updateService.update(ME, courseId,
			new CourseInput(version(courseId), "강릉 바다", null, true, previewHash(courseId, "강릉 바다"), null));
		flush();
		assertThat(searchService.search(ALL, null, null).items()).extracting(CourseSearchResponse.Item::title)
			.containsExactly("강릉 바다");

		updateService.update(ME, courseId, new CourseInput(version(courseId), null, null, null, null, false));
		flush();

		assertThat(searchService.search(ALL, null, null).items()).isEmpty();
		assertThat(searchService.search(ALL, null, null).totalCount()).isZero();
		assertThatThrownBy(() -> detailService.getDetail(courseId)).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.COURSE_UNAVAILABLE);
	}
}
