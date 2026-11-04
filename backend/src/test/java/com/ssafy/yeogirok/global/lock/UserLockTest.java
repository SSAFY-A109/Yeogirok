package com.ssafy.yeogirok.global.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.TestUsers;

/** 사용자 잠금(users 행 FOR UPDATE). 동시 대기 확인은 로컬 PostgreSQL 에서만 돌린다({@code SPRING_TEST_DATABASE_REPLACE=NONE}). */
@DataJpaTest
@Import(UserLock.class)
class UserLockTest {

	@Autowired
	UserLock userLock;

	@Autowired
	TestEntityManager em;

	@Autowired
	DataSource dataSource;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void 활동_중인_회원은_잠그고_없거나_탈퇴했으면_A003() {
		TestUsers.insertActive(em.getEntityManager(), 1001L);
		TestUsers.insert(em.getEntityManager(), 1002L, "WITHDRAWN");

		userLock.lock(1001L);
		for (long userId : new long[] {1002L, 1003L}) {
			assertThatThrownBy(() -> userLock.lock(userId))
				.isInstanceOf(BusinessException.class)
				.extracting("exceptionType").isEqualTo(ExceptionType.ACCESS_TOKEN_INVALID);
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 트랜잭션_밖에서_부르면_예외다() {
		assertThatThrownBy(() -> userLock.lock(1L)).isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void PostgreSQL에서_같은_사용자의_두_번째_트랜잭션은_첫_번째가_끝날_때까지_기다린다() throws Exception {
		assumeTrue(isPostgres(), "PostgreSQL 에서만 확인한다");
		long userId = 434343L;
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		TestUsers.insertActive(jdbc, userId);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch firstLocked = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Long> firstReleasedAt = executor.submit(() -> tx.execute(status -> {
				userLock.lock(userId);
				firstLocked.countDown();
				sleep(500);
				return System.nanoTime();  // 이 직후 커밋하며 잠금이 풀린다
			}));
			assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Long> secondAcquiredAt = executor.submit(() -> tx.execute(status -> {
				userLock.lock(userId);
				return System.nanoTime();
			}));

			assertThat(secondAcquiredAt.get(5, TimeUnit.SECONDS)).isGreaterThan(firstReleasedAt.get(5, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
			TestUsers.delete(jdbc, userId);
		}
	}

	private boolean isPostgres() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
