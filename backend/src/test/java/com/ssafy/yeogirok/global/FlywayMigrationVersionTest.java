package com.ssafy.yeogirok.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Flyway 파일 번호가 겹치면 서버가 시작하지 못한다. 테스트 DB(H2)는 마이그레이션을 돌리지 않아 브랜치 두 개가 같은 번호를
 * 따로 머지해도 테스트로는 드러나지 않으므로 파일 이름만 따로 본다.
 */
class FlywayMigrationVersionTest {

	private static final Pattern VERSIONED = Pattern.compile("V(\\d+(?:[._]\\d+)*)__.+\\.sql");

	@Test
	void 마이그레이션_파일_번호가_겹치지_않는다() throws IOException {
		Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql");
		Map<String, List<String>> byVersion = Arrays.stream(files)
			.map(Resource::getFilename)
			.collect(Collectors.groupingBy(FlywayMigrationVersionTest::versionOf));

		assertThat(files).isNotEmpty();
		assertThat(byVersion).allSatisfy((version, names) -> assertThat(names).as("V%s", version).hasSize(1));
	}

	private static String versionOf(String fileName) {
		Matcher matcher = VERSIONED.matcher(fileName);
		assertThat(matcher.matches()).as("Flyway 파일 이름 형식(V<번호>__<설명>.sql): %s", fileName).isTrue();
		// Flyway 는 V1_1 과 V1.1 을 같은 번호로 본다.
		return matcher.group(1).replace('_', '.');
	}
}
