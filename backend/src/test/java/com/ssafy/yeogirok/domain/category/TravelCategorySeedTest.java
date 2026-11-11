package com.ssafy.yeogirok.domain.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * API-079 목록은 TravelCategory 에 있고 Flyway V2 가 행을 넣은 코드만 보여 준다. 둘이 어긋나면 카테고리가 목록에서 빠진다.
 * 테스트 DB(H2)는 마이그레이션을 돌리지 않아 파일을 직접 읽는다.
 */
class TravelCategorySeedTest {

	private static final String SEED = "db/migration/V2__seed_travel_categories.sql";
	private static final Pattern ROW = Pattern.compile("\\('([A-Z_]+)',\\s*'[^']+'\\)");

	@Test
	void V2_기준_데이터의_코드가_enum과_같다() throws IOException {
		String sql = new ClassPathResource(SEED).getContentAsString(StandardCharsets.UTF_8);
		List<String> codes = new ArrayList<>();
		Matcher row = ROW.matcher(sql);
		while (row.find()) {
			codes.add(row.group(1));
		}

		assertThat(codes).containsExactlyElementsOf(
			Arrays.stream(TravelCategory.values()).map(Enum::name).toList());
	}
}
