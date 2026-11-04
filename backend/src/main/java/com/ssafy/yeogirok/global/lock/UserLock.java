package com.ssafy.yeogirok.global.lock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 사용자 단위 잠금. users 테이블이 없어 지금은 {@code pg_advisory_xact_lock(userId)} 이고 트랜잭션이 끝나면 풀린다.
 * TODO: 회원 기능이 생기면 users 행 {@code SELECT ... FOR UPDATE} 로 바꾼다. 탈퇴 처리도 같은 잠금을 써야 한다.
 */
@Component
public class UserLock {

	@PersistenceContext
	private EntityManager entityManager;

	/** 호출한 트랜잭션이 끝날 때까지 이 사용자를 잠근다. 트랜잭션 밖에서 부르면 예외다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(Long userId) {
		// pg_advisory_xact_lock 은 void 를 돌려줘서 그대로 읽으면 타입 매핑이 안 된다. 행 수로 받는다.
		entityManager.createNativeQuery("SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:userId)) AS user_lock")
			.setParameter("userId", userId)
			.getSingleResult();
	}
}
