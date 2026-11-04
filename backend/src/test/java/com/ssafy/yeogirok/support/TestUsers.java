package com.ssafy.yeogirok.support;

import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;

/** 발자취 테스트용 회원 행. 사용자 잠금(UserLock)이 users 행을 읽으므로 먼저 넣어 둔다. */
public final class TestUsers {

	private static final String INSERT = "INSERT INTO users (id, provider, provider_subject, nickname, status, created_at,"
		+ " updated_at) VALUES (?1, 'KAKAO', ?2, '테스트', ?3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

	private TestUsers() {
	}

	/** 테스트 트랜잭션 안에서 넣는다(끝나면 롤백). */
	public static void insert(EntityManager em, long id, String status) {
		em.createNativeQuery(INSERT)
			.setParameter(1, id)
			.setParameter(2, "test-" + id)
			.setParameter(3, status)
			.executeUpdate();
	}

	public static void insertActive(EntityManager em, long... ids) {
		for (long id : ids) {
			insert(em, id, "ACTIVE");
		}
	}

	/** 실제 커밋이 필요한 동시성 테스트용. 끝나면 {@link #delete} 로 지운다. */
	public static void insertActive(JdbcTemplate jdbc, long id) {
		jdbc.update(INSERT.replace("?1", "?").replace("?2", "?").replace("?3", "?"), id, "test-" + id, "ACTIVE");
	}

	public static void delete(JdbcTemplate jdbc, long id) {
		jdbc.update("DELETE FROM footprints WHERE user_id = ?", id);
		jdbc.update("DELETE FROM users WHERE id = ?", id);
	}
}
