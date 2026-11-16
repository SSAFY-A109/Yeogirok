package com.ssafy.yeogirok.domain.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * API-078 기준 데이터(Flyway V4) 모양 검사. 테스트 DB(H2)는 마이그레이션을 돌리지 않아 파일을 직접 읽는다.
 * 추천 조건 저장(API-033)은 시·군·구 5자리를 받고 앞 2자리를 시·도로 쓰므로 시·군·구 code 는 부모 code 로 시작해야 한다.
 */
class RegionSeedTest {

	private static final String SEED = "db/migration/V4__create_regions.sql";
	private static final Pattern ROW = Pattern.compile(
		"\\('(\\d+)', '[^']+', '(PROVINCE|DISTRICT)', (?:null|'(\\d+)'), \\d+\\)");

	record Row(String code, String level, String parentCode) {
	}

	private static List<Row> rows() throws IOException {
		String sql = new ClassPathResource(SEED).getContentAsString(StandardCharsets.UTF_8);
		List<Row> rows = new ArrayList<>();
		Matcher m = ROW.matcher(sql);
		while (m.find()) {
			rows.add(new Row(m.group(1), m.group(2), m.group(3)));
		}
		return rows;
	}

	private static Set<String> provinceCodes(List<Row> rows) {
		return rows.stream().filter(r -> r.level().equals("PROVINCE")).map(Row::code).collect(Collectors.toSet());
	}

	@Test
	void 시도는_2자리이고_부모가_없다() throws IOException {
		List<Row> provinces = rows().stream().filter(r -> r.level().equals("PROVINCE")).toList();

		assertThat(provinces).hasSize(16).allSatisfy(r -> {
			assertThat(r.code()).hasSize(2);
			assertThat(r.parentCode()).isNull();
		});
	}

	@Test
	void 시군구는_5자리이고_부모_시도_code로_시작한다() throws IOException {
		List<Row> rows = rows();
		Set<String> provinces = provinceCodes(rows);

		assertThat(rows.stream().filter(r -> r.level().equals("DISTRICT")).toList()).isNotEmpty().allSatisfy(r -> {
			assertThat(r.code()).hasSize(5).startsWith(r.parentCode());
			assertThat(provinces).contains(r.parentCode());
		});
	}

	@Test
	void code는_겹치지_않고_모든_시도에_시군구가_있다() throws IOException {
		List<Row> rows = rows();
		Set<String> parents = rows.stream().map(Row::parentCode).filter(p -> p != null).collect(Collectors.toSet());

		assertThat(rows).extracting(Row::code).doesNotHaveDuplicates();
		assertThat(provinceCodes(rows)).allMatch(parents::contains);
	}
}
