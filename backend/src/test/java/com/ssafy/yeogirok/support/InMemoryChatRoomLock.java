package com.ssafy.yeogirok.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ssafy.yeogirok.domain.chat.service.ChatRoomLock;

/**
 * H2 에는 PostgreSQL advisory lock 이 없어 테스트에서 쓰는 채팅 잠금. 방마다 JVM 잠금을 잡고 트랜잭션이 끝날 때 푼다
 * (운영의 pg_advisory_xact_lock 과 같은 수명).
 */
public class InMemoryChatRoomLock implements ChatRoomLock {

	private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

	@Override
	public void lock(Long roomId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("트랜잭션 안에서 불러야 합니다.");
		}
		ReentrantLock lock = locks.computeIfAbsent(roomId, id -> new ReentrantLock());
		lock.lock();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				lock.unlock();
			}
		});
	}
}
