package com.ssafy.yeogirok.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * 운영 DB(PostgreSQL)에서 탈퇴자 보존분 SQL 의 동시성(같은 키를 동시에 만들 때)을 JDBC 로 직접 본다. Hibernate 를 거친
 * 운영 경로 전체는 {@link WithdrawnFootprintsPostgresTest} 가 본다. 테이블은 Flyway V1 의 footprint_statistics 와 같다.
 */
class PostgresPreservedVisitCountsTest {

	private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
		.withEnv("POSTGRES_PASSWORD", "test")
		.withExposedPorts(5432);

	/** db/migration/V1__init.sql 의 footprint_statistics. */
	private static final String DDL = """
		create table footprint_statistics (
		    center_latitude numeric(10,7) not null,
		    center_longitude numeric(10,7) not null,
		    stat_date date not null,
		    updated_at timestamp(6) with time zone not null,
		    visit_count bigint not null,
		    aggregation_version varchar(50) not null,
		    cell_id varchar(255) not null,
		    category_codes jsonb not null,
		    primary key (aggregation_version, category_codes, cell_id, stat_date)
		)
		""";

	@BeforeAll
	static void start() throws SQLException {
		POSTGRES.start();
		try (Connection connection = connect()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute(DDL);
			}
			connection.commit();
		}
	}

	@AfterAll
	static void stop() {
		POSTGRES.stop();
	}

	@BeforeEach
	void clear() throws SQLException {
		try (Connection connection = connect(); Statement statement = connection.createStatement()) {
			statement.execute("delete from footprint_statistics");
			connection.commit();
		}
	}

	@Test
	void 없는_키는_만들고_있는_키는_횟수를_더한다() throws SQLException {
		try (Connection connection = connect()) {
			add(connection, "[\"CAFE\",\"FOOD\"]", 2);
			// jsonb 는 공백이 달라도 같은 값이라 같은 키다.
			add(connection, "[\"CAFE\", \"FOOD\"]", 3);
			add(connection, "[\"CAFE\"]", 1);
			connection.commit();
		}

		assertThat(countOf("[\"CAFE\",\"FOOD\"]")).isEqualTo(5);
		assertThat(countOf("[\"CAFE\"]")).isEqualTo(1);
	}

	@Test
	void 같은_키를_동시에_만들면_뒤_트랜잭션이_기다렸다가_더한다() throws Exception {
		try (Connection first = connect(); Connection second = connect()) {
			add(first, "[\"CAFE\"]", 2);

			CompletableFuture<Void> concurrent = CompletableFuture.runAsync(() -> {
				try {
					add(second, "[\"CAFE\"]", 3);
				} catch (SQLException e) {
					throw new IllegalStateException(e);
				}
			});

			// 첫 트랜잭션이 끝나기 전에는 같은 키를 쓰지 못한다.
			assertThat(waitFor(concurrent, 500)).isFalse();
			first.commit();
			// 유니크 위반 없이 이어서 더한다.
			assertThat(waitFor(concurrent, 5_000)).isTrue();
			second.commit();
		}

		assertThat(countOf("[\"CAFE\"]")).isEqualTo(5);
	}

	/** 운영 코드와 같은 SQL(위치 파라미터만 JDBC 형식으로). */
	private static void add(Connection connection, String categoryCodes, long visitCount) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(PostgresPreservedVisitCounts.sql(1)
			.replaceAll("\\?\\d+", "?"))) {
			statement.setString(1, "test-v1");
			statement.setString(2, "3750:12700");
			statement.setObject(3, LocalDate.parse("2026-11-04"));
			statement.setString(4, categoryCodes);
			statement.setBigDecimal(5, new BigDecimal("37.5050000"));
			statement.setBigDecimal(6, new BigDecimal("127.0050000"));
			statement.setLong(7, visitCount);
			statement.setObject(8, OffsetDateTime.of(2026, 11, 12, 0, 0, 0, 0, ZoneOffset.UTC));
			statement.executeUpdate();
		}
	}

	private static long countOf(String categoryCodes) throws SQLException {
		try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
			"select visit_count from footprint_statistics where category_codes = cast(? as jsonb)")) {
			statement.setString(1, categoryCodes);
			try (ResultSet result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				return result.getLong(1);
			}
		}
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
