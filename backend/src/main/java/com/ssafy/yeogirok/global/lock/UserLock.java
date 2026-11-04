package com.ssafy.yeogirok.global.lock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 사용자 단위 잠금. 같은 사용자의 요청을 트랜잭션 끝까지 한 줄로 세운다
 * (발자취 생성·삭제·탈퇴 처리처럼 "사용자 행 잠금 → 자원 행 잠금" 순서가 필요한 곳).
 *
 * <p>users 테이블이 아직 없어서 PostgreSQL 트랜잭션 advisory lock({@code pg_advisory_xact_lock(userId)})을 쓴다.
 * 커밋·롤백 때 자동으로 풀린다. 키 공간은 사용자 ID 하나만 쓰므로 다른 기능이 같은 함수를 쓰려면 두 정수 키 형태를 쓴다.
 *
 * <p>TODO: 회원 기능이 생기면 users 행 {@code SELECT ... FOR UPDATE} 로 바꾼다(탈퇴 처리와 같은 잠금). 이 클래스만 고친다.
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
