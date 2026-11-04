package com.ssafy.yeogirok.domain.footprint.repository;

import static com.ssafy.yeogirok.domain.footprint.FootprintFixture.footprintOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import jakarta.persistence.PersistenceException;

/**
 * footprints 매핑. 테스트 DB 는 H2 라서 타입 이름(jsonb·timestamptz)은 PostgreSQL 과 다르고,
 * 여기서는 컬럼 이름·NOT NULL·길이·정밀도·CHECK 를 본다.
 */
@DataJpaTest
class FootprintRepositoryTest {

	@Autowired
	TestEntityManager em;

	@Test
	void 컬럼_이름과_NOT_NULL이_DDL과_같다() {
		Map<String, Boolean> nullable = new HashMap<>();
		for (Object[] row : columns()) {
			nullable.put(((String)row[0]).toLowerCase(), "YES".equals(row[1]));
		}

		assertThat(nullable).isEqualTo(Map.ofEntries(
			Map.entry("id", false),
			Map.entry("user_id", false),
			Map.entry("latitude", false),
			Map.entry("longitude", false),
			Map.entry("location_name", true),
			Map.entry("address", true),
			Map.entry("legal_area_code", true),
			Map.entry("body", true),
			Map.entry("category_codes", false),
			Map.entry("visited_at", false),
			Map.entry("location_captured_at", false),
			Map.entry("location_policy_version", false),
			Map.entry("deleted_at", true),
			Map.entry("version", false),
			Map.entry("created_at", false),
			Map.entry("updated_at", false)));
	}

	@Test
	void 길이와_정밀도가_DDL과_같다() {
		Map<String, Object[]> byName = new HashMap<>();
		for (Object[] row : columns()) {
			byName.put(((String)row[0]).toLowerCase(), row);
		}

		assertThat(((Number)byName.get("location_name")[2]).intValue()).isEqualTo(255);
		assertThat(((Number)byName.get("legal_area_code")[2]).intValue()).isEqualTo(10);
		assertThat(((Number)byName.get("location_policy_version")[2]).intValue()).isEqualTo(50);
		for (String coordinate : List.of("latitude", "longitude")) {
			assertThat(((Number)byName.get(coordinate)[3]).intValue()).as(coordinate).isEqualTo(10);
			assertThat(((Number)byName.get(coordinate)[4]).intValue()).as(coordinate).isEqualTo(7);
		}
	}

	@Test
	void CHECK_제약이_있다() {
		Long id = em.persistAndGetId(footprintOf(1L).build(), Long.class);
		em.flush();

		assertThatThrownBy(() -> update(id, "body = '" + "가".repeat(201) + "'"))
			.isInstanceOf(PersistenceException.class);
		assertThatThrownBy(() -> update(id, "latitude = 90.0000001"))
			.isInstanceOf(PersistenceException.class);
		assertThatThrownBy(() -> update(id, "longitude = -180.0000001"))
			.isInstanceOf(PersistenceException.class);
		assertThatThrownBy(() -> update(id, "version = 0"))
			.isInstanceOf(PersistenceException.class);
	}

	private void update(Long id, String set) {
		em.getEntityManager().createNativeQuery("UPDATE footprints SET " + set + " WHERE id = " + id).executeUpdate();
	}

	@SuppressWarnings("unchecked")
	private List<Object[]> columns() {
		return em.getEntityManager().createNativeQuery("""
				SELECT column_name, is_nullable, character_maximum_length, numeric_precision, numeric_scale
				FROM information_schema.columns
				WHERE LOWER(table_name) = 'footprints'
				""")
			.getResultList();
	}
}
