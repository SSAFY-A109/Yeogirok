package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.StoryEditResponse;
import com.ssafy.yeogirok.domain.record.dto.StoryWriteResponse;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitStory;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;

/** API-069·070. 본문 형식 오류는 RecordControllerTest 가 본다. */
@DataJpaTest
@Import(RecordTestConfig.class)
class StoryServiceTest {

	static final Long ME = 9001L;
	static final Long FRIEND = 9002L;
	static final Long LEFT = 9003L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");

	@Autowired
	StoryService storyService;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	Long recordId;
	Long visitId;
	Long otherRecordVisitId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		recordId = record(true);
		visitId = visit(recordId);
		otherRecordVisitId = visit(record(false));
		em.flush();
	}

	/** @param joined ME·FRIEND 가 참여하는 방이면 true */
	private Long record(boolean joined) {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY1)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		if (joined) {
			em.persist(RoomMember.creator(room.getId(), ME, NOW));
			em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
			em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		}
		return em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
	}

	private Long visit(Long recordId) {
		ActualVisit visit = ActualVisit.manual(recordId, 1L, DAY1, NOW, NOW);
		visit.moveTo(1, NOW);
		return em.persist(visit).getId();
	}

	private TravelRecord record() {
		em.flush();
		em.clear();
		return em.find(TravelRecord.class, recordId);
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 인증_사용자를_작성자로_저장하고_완성한_기록은_DRAFT로_돌아간다() {
		em.find(TravelRecord.class, recordId).complete(NOW);
		em.flush();

		StoryWriteResponse response = storyService.write(ME, recordId, visitId, "바다가 좋았다");

		assertThat(response.authorId()).isEqualTo(ME);
		assertThat(response.body()).isEqualTo("바다가 좋았다");
		assertThat(response.status()).isEqualTo(RecordStatus.DRAFT);
		assertThat(record().getVersion()).isEqualTo(3);
		VisitStory saved = em.find(VisitStory.class, response.storyId());
		assertThat(saved.getVisitId()).isEqualTo(visitId);
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void 같은_사람이_같은_방문에_여러_글을_쓸_수_있다() {
		Long first = storyService.write(ME, recordId, visitId, "하나").storyId();
		Long second = storyService.write(ME, recordId, visitId, "둘").storyId();

		assertThat(first).isNotEqualTo(second);
		assertThat(record().getVersion()).isEqualTo(3);
	}

	@Test
	void 작성_다른_기록의_방문은_RE013_나간_사람은_RE003() {
		assertError(() -> storyService.write(ME, recordId, otherRecordVisitId, "x"), ExceptionType.VISIT_NOT_FOUND);
		assertError(() -> storyService.write(LEFT, recordId, visitId, "x"), ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	@Test
	void 작성_멱등_재전송은_그때_쓴_글과_지금_기록_상태를_준다() {
		StoryWriteResponse first = storyService.write(ME, recordId, visitId, "바다");

		assertThat(storyService.getWritten(ME, recordId, first.storyId())).isEqualTo(first);
		assertError(() -> storyService.getWritten(LEFT, recordId, first.storyId()),
			ExceptionType.RECORD_EDIT_FORBIDDEN);
	}

	@Test
	void 본인_글을_고치면_updatedAt이_바뀌고_기록_version이_오른다() {
		Long storyId = storyService.write(ME, recordId, visitId, "처음").storyId();
		clock.advance(Duration.ofMinutes(5));

		StoryEditResponse response = storyService.edit(ME, recordId, visitId, storyId, "고친 글");

		assertThat(response.body()).isEqualTo("고친 글");
		assertThat(response.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
		assertThat(response.status()).isEqualTo(RecordStatus.DRAFT);
		assertThat(record().getVersion()).isEqualTo(3);
	}

	@Test
	void 같은_본문이면_바꾸지_않고_완성_상태를_그대로_둔다() {
		Long storyId = storyService.write(ME, recordId, visitId, "처음").storyId();
		em.find(TravelRecord.class, recordId).complete(NOW);
		em.flush();

		StoryEditResponse response = storyService.edit(ME, recordId, visitId, storyId, "처음");

		assertThat(response.status()).isEqualTo(RecordStatus.COMPLETED);
		assertThat(response.updatedAt()).isEqualTo(NOW);
		assertThat(record().getVersion()).isEqualTo(3);
	}

	@Test
	void 다른_사람_글은_RE015_그_방문에_없는_글은_RE014() {
		Long friendStory = storyService.write(FRIEND, recordId, visitId, "친구 글").storyId();

		assertError(() -> storyService.edit(ME, recordId, visitId, friendStory, "덮어쓰기"),
			ExceptionType.STORY_EDIT_FORBIDDEN);
		assertError(() -> storyService.edit(FRIEND, recordId, otherRecordVisitId, friendStory, "x"),
			ExceptionType.STORY_NOT_FOUND);
		assertError(() -> storyService.edit(FRIEND, recordId, visitId, friendStory + 100, "x"),
			ExceptionType.STORY_NOT_FOUND);
	}
}
