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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.support.H2PostgresFunctions;

/**
 * 사용자 잠금. 실제 잠금 동작은 PostgreSQL 에서만 확인할 수 있어 해당 테스트는 H2 에서 건너뛴다
 * ({@code SPRING_TEST_DATABASE_REPLACE=NONE} 으로 로컬 PostgreSQL 에서 돌린다).
 */
@DataJpaTest
@Import({UserLock.class, H2PostgresFunctions.class})
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
	void 트랜잭션_안에서는_잠글_수_있다() {
		userLock.lock(1L);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 트랜잭션_밖에서_부르면_예외다() {
		assertThatThrownBy(() -> userLock.lock(1L)).isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void PostgreSQL에서는_트랜잭션_advisory_lock을_잡는다() throws Exception {
		assumeTrue(isPostgres(), "PostgreSQL 에서만 확인한다");

		userLock.lock(424242L);

		Number held = (Number)em.getEntityManager().createNativeQuery("""
				SELECT COUNT(*) FROM pg_locks
				WHERE locktype = 'advisory' AND granted AND pid = pg_backend_pid() AND objid = 424242
				""")
			.getSingleResult();
		assertThat(held.intValue()).isEqualTo(1);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void PostgreSQL에서_같은_사용자의_두_번째_트랜잭션은_첫_번째가_끝날_때까지_기다린다() throws Exception {
		assumeTrue(isPostgres(), "PostgreSQL 에서만 확인한다");
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch firstLocked = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Long> firstReleasedAt = executor.submit(() -> tx.execute(status -> {
				userLock.lock(434343L);
				firstLocked.countDown();
				sleep(500);
				return System.nanoTime();  // 이 직후 커밋하며 잠금이 풀린다
			}));
			assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Long> secondAcquiredAt = executor.submit(() -> tx.execute(status -> {
				userLock.lock(434343L);
				return System.nanoTime();
			}));

			assertThat(secondAcquiredAt.get(5, TimeUnit.SECONDS)).isGreaterThan(firstReleasedAt.get(5, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
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
