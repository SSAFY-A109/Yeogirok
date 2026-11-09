package com.ssafy.yeogirok.domain.tip.repository;

import static com.ssafy.yeogirok.domain.tip.TipFixture.published;
import static com.ssafy.yeogirok.domain.tip.TipFixture.registering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;

import jakarta.persistence.PersistenceException;

/**
 * ar_tips·ar_tip_reactions·ar_tip_images 매핑. 테스트 DB 는 H2 라서 타입 이름(ENUM·jsonb)은 PostgreSQL 과 다르고,
 * 여기서는 컬럼 이름·NOT NULL·길이·정밀도·CHECK 를 본다.
 */
@DataJpaTest
class ArTipRepositoryTest {

	@Autowired
	TestEntityManager em;

	@Test
	void ar_tips_컬럼_이름과_NOT_NULL이_DDL과_같다() {
		assertThat(nullable("ar_tips")).isEqualTo(Map.ofEntries(
			Map.entry("id", false),
			Map.entry("author_id", false),
			Map.entry("category_code", false),
			Map.entry("body", false),
			Map.entry("latitude", false),
			Map.entry("longitude", false),
			Map.entry("location_captured_at", false),
			Map.entry("accuracy_meters", false),
			Map.entry("location_policy_version", false),
			Map.entry("spatial_provider", false),
			Map.entry("spatial_reference_id", true),
			Map.entry("spatial_expires_at", true),
			Map.entry("placement", false),
			Map.entry("status", false),
			Map.entry("version", false),
			Map.entry("published_at", true),
			Map.entry("deleted_at", true),
			Map.entry("created_at", false),
			Map.entry("updated_at", false)));
	}

	@Test
	void 반응_사진_테이블_컬럼이_DDL과_같다() {
		assertThat(nullable("ar_tip_reactions")).isEqualTo(Map.of(
			"tip_id", false, "user_id", false, "reaction", false, "updated_at", false));
		assertThat(nullable("ar_tip_images")).isEqualTo(Map.of(
			"id", false, "tip_id", false, "image_path", false, "created_at", false));
	}

	@Test
	void 길이와_정밀도가_DDL과_같다() {
		Map<String, Object[]> byName = new HashMap<>();
		for (Object[] row : columns("ar_tips")) {
			byName.put(((String)row[0]).toLowerCase(), row);
		}

		assertThat(((Number)byName.get("location_policy_version")[2]).intValue()).isEqualTo(50);
		assertThat(((Number)byName.get("spatial_provider")[2]).intValue()).isEqualTo(50);
		assertThat(((Number)byName.get("spatial_reference_id")[2]).intValue()).isEqualTo(255);
		for (String coordinate : List.of("latitude", "longitude")) {
			assertThat(((Number)byName.get(coordinate)[3]).intValue()).as(coordinate).isEqualTo(10);
			assertThat(((Number)byName.get(coordinate)[4]).intValue()).as(coordinate).isEqualTo(7);
		}
		assertThat(((Number)byName.get("accuracy_meters")[3]).intValue()).isEqualTo(10);
		assertThat(((Number)byName.get("accuracy_meters")[4]).intValue()).isEqualTo(2);
	}

	@Test
	void CHECK_제약이_있다() {
		Long id = em.persistAndGetId(registering(1L), Long.class);
		em.flush();

		assertThatThrownBy(() -> update(id, "body = '" + "가".repeat(121) + "'"))
			.isInstanceOf(PersistenceException.class);
		assertThatThrownBy(() -> update(id, "body = '   '"))
			.isInstanceOf(PersistenceException.class);
		assertThatThrownBy(() -> update(id, "latitude = 90.0000001"))
			.isInstanceOf(PersistenceException.class);
		assertThatThrownBy(() -> update(id, "accuracy_meters = -0.01"))
			.isInstanceOf(PersistenceException.class);
		assertThatThrownBy(() -> update(id, "status = 'PUBLISHED'"))
			.as("published_at 없이 PUBLISHED").isInstanceOf(PersistenceException.class);
		assertThatThrownBy(() -> update(id, "version = 0"))
			.isInstanceOf(PersistenceException.class);
	}

	@Test
	void 게시하면_placement와_공간_참조를_그대로_다시_읽는다() {
		Long id = em.persistAndGetId(published(1L), Long.class);
		em.flush();
		em.clear();

		ArTip found = em.find(ArTip.class, id);
		assertThat(found.getPlacement()).containsEntry("anchorType", "CLOUD").containsEntry("schemaVersion", 1);
		assertThat(found.spatialReference().referenceId()).isEqualTo("anchor-1");
		assertThat(found.getPublishedAt()).isNotNull();
	}

	private void update(Long id, String set) {
		em.getEntityManager().createNativeQuery("UPDATE ar_tips SET " + set + " WHERE id = " + id).executeUpdate();
	}

	private Map<String, Boolean> nullable(String table) {
		Map<String, Boolean> nullable = new HashMap<>();
		for (Object[] row : columns(table)) {
			nullable.put(((String)row[0]).toLowerCase(), "YES".equals(row[1]));
		}
		return nullable;
	}

	@SuppressWarnings("unchecked")
	private List<Object[]> columns(String table) {
		return em.getEntityManager().createNativeQuery("""
				SELECT column_name, is_nullable, character_maximum_length, numeric_precision, numeric_scale
				FROM information_schema.columns
				WHERE LOWER(table_name) = :table
				""")
			.setParameter("table", table)
			.getResultList();
	}
}
