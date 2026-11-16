package com.ssafy.yeogirok.support;

import jakarta.persistence.EntityManager;

/**
 * 추천 조건 저장(API-033)이 쓰는 지역 목록 행(강원 51 · 강릉 51150 · 속초 51210). 저장은 목록의 시·군·구 code 만 받는다.
 * 기준 데이터라 없을 때만 넣고 지우지 않는다(PostgreSQL 이면 Flyway V4 가 이미 넣었다). 커밋하는 테스트에서도 다시 불러도 된다.
 */
public final class TestRegions {

	private TestRegions() {
	}

	public static void insertGangwon(EntityManager em) {
		insert(em, "51", "강원특별자치도", "PROVINCE", null, 10);
		insert(em, "51150", "강릉시", "DISTRICT", "51", 3);
		insert(em, "51210", "속초시", "DISTRICT", "51", 6);
	}

	private static void insert(EntityManager em, String code, String name, String level, String parentCode,
		int displayOrder) {
		Number count = (Number)em.createNativeQuery("SELECT COUNT(*) FROM regions WHERE code = ?1")
			.setParameter(1, code)
			.getSingleResult();
		if (count.longValue() > 0) {
			return;
		}
		// 시·도는 parent_code 가 null 이다. 타입 없는 null 바인딩을 피하려고 SQL 에 NULL 을 쓴다.
		em.createNativeQuery("INSERT INTO regions (code, name, level, parent_code, display_order)"
				+ " VALUES (?1, ?2, ?3, " + (parentCode == null ? "NULL" : "'" + parentCode + "'") + ", ?4)")
			.setParameter(1, code)
			.setParameter(2, name)
			.setParameter(3, level)
			.setParameter(4, displayOrder)
			.executeUpdate();
	}
}
