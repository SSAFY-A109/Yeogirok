package com.ssafy.yeogirok.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * 운영 DB(PostgreSQL)에 실제 마이그레이션 파일(db/migration)을 적용해 본다. 다른 테스트는 H2 에 엔티티로 테이블을 만들어
 * 마이그레이션을 실행하지 않는다(docs/08-flyway.md 1절).
 *
 * <p>V5(room_places.source_record_id FK): V4 까지 적용한 DB 에 원본이 있는 후보·원본이 없는 후보를 넣고 V5 를 적용한다.
 * 없는 기록을 가리키던 값은 비워지고, FK 가 걸리고, 원본 기록을 지우면 후보는 남고 출처만 비워져야 한다(ON DELETE SET NULL).
 */
class FlywayMigrationTest {

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

	private static String url() {
		return "jdbc:postgresql://%s:%d/postgres".formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432));
	}

	/** 컨테이너가 연결을 받을 때까지 잠깐 기다린다. */
	private static Connection connect() throws SQLException {
		for (int attempt = 0; ; attempt++) {
			try {
				return DriverManager.getConnection(url(), "postgres", "test");
			} catch (SQLException e) {
				if (attempt > 50) {
					throw e;
				}
				try {
					Thread.sleep(200);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	private static void migrate(String target) throws SQLException {
		connect().close();
		Flyway.configure().dataSource(url(), "postgres", "test").locations("classpath:db/migration")
			.target(target).load().migrate();
	}

	private static Map<Long, Long> sourceRecordIds(Statement statement) throws SQLException {
		Map<Long, Long> result = new LinkedHashMap<>();
		try (ResultSet rows = statement.executeQuery("select id, source_record_id from room_places order by id")) {
			while (rows.next()) {
				long id = rows.getLong(1);
				long sourceRecordId = rows.getLong(2);
				result.put(id, rows.wasNull() ? null : sourceRecordId);
			}
		}
		return result;
	}

	@Test
	void 모든_마이그레이션이_적용되고_V5_는_고아_출처를_비우고_원본_삭제_시_출처만_비운다() throws SQLException {
		migrate("4");
		try (Connection connection = connect(); Statement statement = connection.createStatement()) {
			statement.execute("insert into travel_records (id, version, created_at, room_id, updated_at, status, title,"
				+ " description) values (201, 1, now(), 1, now(), 'COMPLETED', '강릉 1박', '')");
			statement.execute("insert into places (id, latitude, longitude, created_at, parking_status, provider, name,"
				+ " parking_info) values (1, 37.7, 128.9, now(), 'UNKNOWN', 'MANUAL', '경포대', '{}')");
			statement.execute("insert into room_places (id, added_by, created_at, place_id, room_id, source_record_id,"
				+ " updated_at, source_type) values (1, 9, now(), 1, 2, 201, now(), 'COURSE'),"
				+ " (2, 9, now(), 1, 2, 999, now(), 'COURSE'), (3, 9, now(), 1, 2, null, now(), 'MANUAL')");
		}

		migrate("latest");

		try (Connection connection = connect(); Statement statement = connection.createStatement()) {
			Map<Long, Long> afterMigration = new LinkedHashMap<>();
			afterMigration.put(1L, 201L);
			afterMigration.put(2L, null);
			afterMigration.put(3L, null);
			assertThat(sourceRecordIds(statement)).isEqualTo(afterMigration);

			assertThatThrownBy(() -> statement.execute("update room_places set source_record_id = 12345 where id = 3"))
				.isInstanceOf(SQLException.class).hasMessageContaining("fk_room_places_source_record");

			statement.execute("delete from travel_records where id = 201");
			assertThat(sourceRecordIds(statement)).containsEntry(1L, null).hasSize(3);
		}
	}
}
