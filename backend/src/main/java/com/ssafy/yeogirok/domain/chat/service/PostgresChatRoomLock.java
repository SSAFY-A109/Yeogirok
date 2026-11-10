package com.ssafy.yeogirok.domain.chat.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * PostgreSQL 트랜잭션 advisory lock({@code pg_advisory_xact_lock(int, int)}). 첫 키는 채팅 전용 이름공간이라 다른 기능의
 * advisory lock 과 겹치지 않고, 둘째 키는 방 ID 다. 커밋·롤백 때 DB 가 풀어 주므로 따로 풀지 않는다.
 * 테스트(H2)는 {@code InMemoryChatRoomLock} 으로 바꿔 쓴다.
 */
@Component
public class PostgresChatRoomLock implements ChatRoomLock {

	/** "CHAT" 의 ASCII 값. */
	static final int NAMESPACE = 0x43484154;
	/** 함수 결과가 void 라 바로 읽으면 JDBC 타입을 매핑하지 못한다. 한 겹 감싸 정수를 돌려받는다. */
	static final String SQL = "select 1 from (select pg_advisory_xact_lock(?1, ?2)) locked";

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(Long roomId) {
		entityManager.createNativeQuery(SQL)
			.setParameter(1, NAMESPACE)
			.setParameter(2, keyOf(roomId))
			.getSingleResult();
	}

	/** 방 ID 를 int 키로 접는다. 21억을 넘는 ID 가 다른 방과 겹치면 서로 조금 기다릴 뿐 순서는 그대로다. */
	static int keyOf(Long roomId) {
		return (int)(roomId % Integer.MAX_VALUE);
	}
}
