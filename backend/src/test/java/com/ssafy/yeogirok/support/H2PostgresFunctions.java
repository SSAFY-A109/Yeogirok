package com.ssafy.yeogirok.support;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * H2 테스트 DB 에 없는 PostgreSQL 함수를 같은 이름의 빈 함수로 등록한다. PostgreSQL 로 돌릴 때는 아무것도 하지 않는다.
 *
 * <p>잠금 동작 자체는 H2 에서 확인할 수 없다. 실제 잠금은 PostgreSQL 로 돌리는 UserLockTest 가 본다.
 */
@TestConfiguration
public class H2PostgresFunctions {

	@Bean
	InitializingBean registerH2PostgresFunctions(DataSource dataSource) {
		return () -> {
			try (Connection connection = dataSource.getConnection()) {
				if (!"H2".equals(connection.getMetaData().getDatabaseProductName())) {
					return;
				}
				try (Statement statement = connection.createStatement()) {
					statement.execute("CREATE ALIAS IF NOT EXISTS PG_ADVISORY_XACT_LOCK FOR '"
						+ H2PostgresFunctions.class.getName() + ".pgAdvisoryXactLock'");
				}
			}
		};
	}

	/** H2 용 빈 구현. 잠그지 않는다. */
	public static int pgAdvisoryXactLock(long key) {
		return 0;
	}
}
