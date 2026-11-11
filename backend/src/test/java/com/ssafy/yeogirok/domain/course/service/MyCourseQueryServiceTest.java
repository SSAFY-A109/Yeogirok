package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.MyCourseListResponse;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;

/** 181 API-074 내가 참여한 기록의 공개 코스 목록. limit·커서 형식 오류는 058 과 같은 도우미라 여기서 다시 보지 않는다. */
@DataJpaTest
@Import({CourseTestConfig.class, MyCourseQueryService.class})
class MyCourseQueryServiceTest {

	static final long ME = 9601L;
	static final long FRIEND = 9602L;

	@Autowired
	MyCourseQueryService service;
	@Autowired
	TestEntityManager em;

	CourseFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new CourseFixture(em);
	}

	/** 게시한 기록. 방마다 기록이 하나라 방도 새로 만든다. */
	private TravelRecord published(String title, long... members) {
		return published(fixture.room(DAY1, members), title);
	}

	private TravelRecord published(TravelRoom room, String title) {
		TravelRecord record = fixture.record(room);
		record.changeCourse(title, "", NOW, true, NOW);
		return record;
	}

	private MyCourseListResponse mine(String cursor, Integer limit) {
		em.flush();
		em.clear();
		return service.getMine(ME, cursor, limit);
	}

	@Test
	void 참여_중인_방의_게시된_기록만_최근_순으로_작성자와_함께_나온다() {
		TravelRecord byFriend = published("친구가 게시", FRIEND, ME);
		fixture.record(fixture.room(DAY1, ME));
		TravelRecord byMe = published("혼자 코스", ME);

		MyCourseListResponse response = mine(null, null);

		assertThat(response.items()).containsExactly(
			new MyCourseListResponse.Item(byMe.getId(), "혼자 코스", List.of(new PublicAuthor(ME, null)), NOW, 2),
			new MyCourseListResponse.Item(byFriend.getId(), "친구가 게시",
				List.of(new PublicAuthor(ME, null), new PublicAuthor(FRIEND, null)), NOW, 2));
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	void 게시_해제한_기록_나간_방_삭제된_방의_기록은_나오지_않는다() {
		TravelRecord unpublished = published("해제", ME);
		unpublished.changeCourse("해제", "", null, false, NOW);
		TravelRoom left = fixture.room(DAY1, FRIEND, ME);
		published(left, "나간 방");
		em.flush();
		em.getEntityManager().createQuery("select m from RoomMember m where m.roomId = :roomId and m.userId = :userId",
			RoomMember.class).setParameter("roomId", left.getId()).setParameter("userId", ME).getSingleResult().leave(NOW);
		TravelRoom deleted = fixture.room(DAY1, ME);
		published(deleted, "삭제된 방");
		deleted.softDelete(NOW);

		assertThat(mine(null, null).items()).isEmpty();
	}

	@Test
	void 한_묶음보다_많으면_다음_커서로_이어_받는다() {
		TravelRecord first = published("a", ME);
		TravelRecord second = published("b", ME);
		TravelRecord third = published("c", ME);

		MyCourseListResponse page1 = mine(null, 2);
		MyCourseListResponse page2 = mine(page1.nextCursor(), 2);

		assertThat(page1.items()).extracting(MyCourseListResponse.Item::courseId)
			.containsExactly(third.getId(), second.getId());
		assertThat(page1.nextCursor()).isNotNull();
		assertThat(page2.items()).extracting(MyCourseListResponse.Item::courseId).containsExactly(first.getId());
		assertThat(page2.nextCursor()).isNull();
	}

	@Test
	void 딱_한_묶음이면_다음_커서가_없다() {
		published("a", ME);
		published("b", ME);

		assertThat(mine(null, 2).nextCursor()).isNull();
	}
}
