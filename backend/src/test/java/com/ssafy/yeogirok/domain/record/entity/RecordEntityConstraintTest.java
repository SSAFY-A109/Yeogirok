package com.ssafy.yeogirok.domain.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

/** actual_visits·visit_stories 를 Hibernate 가 만든 테이블에 넣어 본다. 제약 위반은 PostgreSQL 이 트랜잭션을 멈추므로 테스트마다 하나만. */
@DataJpaTest
class RecordEntityConstraintTest {

	static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	static final LocalDate DAY = LocalDate.parse("2026-11-01");

	@Autowired
	TestEntityManager em;
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void 수동_방문은_USER_공개_선택_TRUE_로_저장되고_기록_version은_직접_관리한다() {
		TravelRecord record = em.persist(TravelRecord.create(1L, "가을 여행", NOW));
		ActualVisit visit = ActualVisit.manual(record.getId(), 10L, DAY, NOW, NOW);
		visit.moveTo(1, NOW);
		Long visitId = em.persistAndFlush(visit).getId();
		Long storyId = em.persistAndFlush(VisitStory.write(visitId, 7L, "좋았다", NOW)).getId();
		record.markEdited(NOW);
		em.flush();
		em.clear();

		ActualVisit saved = em.find(ActualVisit.class, visitId);
		assertThat(saved.getCreationType()).isEqualTo(VisitCreationType.USER);
		assertThat(saved.isPublicSelected()).isTrue();
		assertThat(saved.getConfirmedAt()).isEqualTo(NOW);
		assertThat(saved.getSourceDraftKey()).isNull();
		assertThat(em.find(VisitStory.class, storyId).getBody()).isEqualTo("좋았다");
		assertThat(em.find(TravelRecord.class, record.getId()).getVersion()).isEqualTo(2);
	}

	@Test
	void 순서는_1_이상이어야_한다() {
		assertThatThrownBy(() -> insertVisit(0, null, null)).satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("actual_visits_position_check"));
	}

	@Test
	void 초안_출처는_둘_다_비었거나_둘_다_있어야_한다() {
		assertThatThrownBy(() -> insertVisit(1, "draft", null)).satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("actual_visits_source_check"));
	}

	@Test
	void 초안_출처가_둘_다_있으면_저장된다() {
		insertVisit(1, "draft", 1);
		assertThat(jdbc.queryForObject("select count(*) from actual_visits", Long.class)).isEqualTo(1);
	}

	private void insertVisit(int position, String draftKey, Integer itemId) {
		jdbc.update("insert into actual_visits (record_id, place_id, creation_type, visit_date, visited_at, position,"
			+ " is_public_selected, confirmed_at, source_draft_key, source_item_id, created_at, updated_at)"
			+ " values (1, 1, 'USER', DATE '2026-11-01', CURRENT_TIMESTAMP, ?, TRUE, CURRENT_TIMESTAMP, ?, ?,"
			+ " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", position, draftKey, itemId);
	}
}
