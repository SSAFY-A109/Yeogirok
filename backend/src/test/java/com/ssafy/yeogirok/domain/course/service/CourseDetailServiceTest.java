package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.CourseDay;
import com.ssafy.yeogirok.domain.course.dto.CourseDetailResponse;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.course.dto.PublicPhoto;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 187 API-016 공개 코스 상세. 응답 모양은 CourseControllerTest 가 본다. */
@DataJpaTest
@Import({CourseTestConfig.class, CourseDetailService.class})
class CourseDetailServiceTest {

	static final long ME = 9801L;
	static final long FRIEND = 9802L;

	@Autowired
	CourseDetailService service;
	@Autowired
	CourseSelection courseSelection;
	@Autowired
	TestEntityManager em;

	CourseFixture fixture;
	TravelRoom room;
	TravelRecord record;
	VisitImage selected;
	VisitImage linkedLater;
	VisitImage unlinked;

	@BeforeEach
	void setUp() {
		fixture = new CourseFixture(em);
		room = fixture.room(DAY1, ME, FRIEND);
		record = fixture.record(room);
		Long visitId = fixture.visit(record.getId(), fixture.place("경포대"), DAY1, 1).getId();
		fixture.visit(record.getId(), fixture.place("안목해변"), DAY1.plusDays(1), 1);
		selected = fixture.photo(record.getId(), visitId, 1);
		unlinked = fixture.photo(record.getId(), null, 0);
		record.complete(NOW);
		record.changeCourse("강릉 1박", "바다", NOW, courseSelection.save(record.getId(), true), NOW);
		// 게시한 뒤 연결한 사진은 공개 설정을 다시 저장하기 전까지 비공개다.
		linkedLater = fixture.photo(record.getId(), visitId, 2);
		em.flush();
		em.clear();
	}

	private CourseDetailResponse detail() {
		em.flush();
		em.clear();
		return service.getDetail(record.getId());
	}

	private static void assertUnavailable(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.COURSE_UNAVAILABLE);
	}

	@Test
	void 게시한_코스를_공개_선택된_사진만_담아_보여준다() {
		CourseDetailResponse response = detail();

		assertThat(response.id()).isEqualTo(record.getId());
		assertThat(response.title()).isEqualTo("강릉 1박");
		assertThat(response.description()).isEqualTo("바다");
		assertThat(response.authors()).containsExactly(new PublicAuthor(ME, null), new PublicAuthor(FRIEND, null));
		assertThat(response.days()).extracting(CourseDay::day).containsExactly(1, 2);
		assertThat(response.days().getFirst().items().getFirst().photos())
			.containsExactly(new PublicPhoto(selected.getId(), RecordTestConfig.SIGNED + selected.getImagePath(), null));
		assertThat(response.days().getFirst().items().getFirst().place().name()).isEqualTo("경포대");
		// 방 기간 3일(DAY1~DAY1+2), 방문이 없는 마지막 날도 센다.
		assertThat(List.of(response.dayCount(), response.nightCount())).containsExactly(3, 2);
		assertThat(response.transportMode()).isEqualTo(TransportMode.CAR);
		assertThat(response.version()).isEqualTo(record.getVersion());
	}

	@Test
	void 미게시_없는_코스_삭제된_방은_CO001() {
		em.find(TravelRecord.class, record.getId()).changeCourse("강릉 1박", "바다", null, false, NOW);
		assertUnavailable(() -> detail());
		assertUnavailable(() -> service.getDetail(record.getId() + 1000));

		em.find(TravelRecord.class, record.getId()).changeCourse("강릉 1박", "바다", NOW, false, NOW);
		em.find(TravelRoom.class, room.getId()).softDelete(NOW);
		assertUnavailable(() -> detail());
	}

	@Test
	void 게시_중이면_편집으로_DRAFT가_돼도_보인다() {
		em.find(TravelRecord.class, record.getId()).markEdited(NOW);

		CourseDetailResponse response = detail();

		assertThat(em.find(TravelRecord.class, record.getId()).getStatus()).isEqualTo(RecordStatus.DRAFT);
		assertThat(response.id()).isEqualTo(record.getId());
	}
}
