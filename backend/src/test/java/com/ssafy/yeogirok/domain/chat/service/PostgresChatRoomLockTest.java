package com.ssafy.yeogirok.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * 운영 DB(PostgreSQL)에서 채팅 잠금 SQL 이 실제로 도는지 본다. H2 에는 advisory lock 이 없어 다른 테스트는 JVM 잠금을 쓴다.
 * 같은 방은 앞 트랜잭션이 끝날 때까지 기다리고, 다른 방은 기다리지 않는다.
 */
class PostgresChatRoomLockTest {

	private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
		.withEnv("POSTGRES_PASSWORD", "test")
		.withExposedPorts(5432);

	@BeforeAll
	static void start() {
		POSTGRES.start();
	}

	@AfterAll
	static void stop() {
		POSTGRES.stop();
	}

	private static Connection connect() throws SQLException {
		Connection connection = null;
		for (int attempt = 0; connection == null; attempt++) {
			try {
				connection = DriverManager.getConnection("jdbc:postgresql://%s:%d/postgres".formatted(
					POSTGRES.getHost(), POSTGRES.getMappedPort(5432)), "postgres", "test");
			} catch (SQLException e) {
				if (attempt > 50) {
					throw e;
				}
				sleep(200);
			}
		}
		connection.setAutoCommit(false);
		return connection;
	}

	/** 운영 코드와 같은 SQL(위치 파라미터만 JDBC 형식으로). */
	private static void lock(Connection connection, long roomId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			PostgresChatRoomLock.SQL.replace("?1", "?").replace("?2", "?"))) {
			statement.setInt(1, PostgresChatRoomLock.NAMESPACE);
			statement.setInt(2, PostgresChatRoomLock.keyOf(roomId));
			try (ResultSet result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isEqualTo(1);
			}
		}
	}

	@Test
	void 같은_방은_앞_트랜잭션이_끝날_때까지_기다리고_다른_방은_기다리지_않는다() throws Exception {
		try (Connection first = connect(); Connection second = connect(); Connection other = connect()) {
			lock(first, 101L);

			CompletableFuture<Void> sameRoom = CompletableFuture.runAsync(() -> {
				try {
					lock(second, 101L);
				} catch (SQLException e) {
					throw new IllegalStateException(e);
				}
			});
			lock(other, 102L);
			other.commit();

			// 같은 방 두 번째 잠금은 첫 트랜잭션이 끝나기 전에는 잡히지 않는다.
			assertThat(waitFor(sameRoom, 500)).isFalse();
			first.commit();
			assertThat(waitFor(sameRoom, 5_000)).isTrue();
			second.commit();
		}
	}

	private static boolean waitFor(CompletableFuture<Void> future, long millis) throws Exception {
		try {
			future.get(millis, TimeUnit.MILLISECONDS);
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
