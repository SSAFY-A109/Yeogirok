package com.ssafy.yeogirok.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ssafy.yeogirok.domain.course.repository.CourseCandidate;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/** 227 메인 추천 고르기(명세 main-api.md 227 AC-01~06). 순수 계산이라 DB 없이 고정 시드로 본다. */
class MainCoursePickerTest {

	static final Random SEEDED = new Random(227);

	static MainCoursePicker.Entry entry(long id, long visits, long photoVisits, long photos, int days) {
		return new MainCoursePicker.Entry(new CourseCandidate(id, "코스" + id, id, null, null, TransportMode.CAR, null, null,
			0L), visits, photoVisits, photos, days);
	}

	/** id 1~ : 사진 넉넉(4방문·4사진, 당일), 101~ : 나머지(4방문·1사진). */
	static List<MainCoursePicker.Entry> pool(int rich, int others) {
		List<MainCoursePicker.Entry> entries = new ArrayList<>();
		IntStream.rangeClosed(1, rich).forEach(i -> entries.add(entry(i, 4, 4, 4, 1)));
		IntStream.rangeClosed(1, others).forEach(i -> entries.add(entry(100 + i, 4, 1, 1, 1)));
		return entries;
	}

	static List<Long> ids(List<CourseCandidate> picked) {
		return picked.stream().map(CourseCandidate::id).toList();
	}

	static boolean isRich(long id) {
		return id < 100;
	}

	/** AC-01: 1·3·5·7번째는 사진 넉넉, 2·4·6·8번째는 나머지, 8개, 중복 없음. 섞임이 달라도 늘 같다. */
	@RepeatedTest(20)
	void 홀수_자리는_사진_넉넉한_코스_짝수_자리는_나머지에서_8개를_중복_없이_고른다() {
		List<Long> picked = ids(MainCoursePicker.pick(pool(10, 10), new Random()));

		assertThat(picked).hasSize(8).doesNotHaveDuplicates();
		for (int i = 0; i < picked.size(); i++) {
			assertThat(isRich(picked.get(i))).as("%d번째", i + 1).isEqualTo(i % 2 == 0);
		}
	}

	@Test
	void 요청마다_새로_섞는다() {
		List<MainCoursePicker.Entry> entries = pool(10, 10);

		assertThat(IntStream.range(0, 20).mapToObj(i -> ids(MainCoursePicker.pick(entries, SEEDED))).distinct().count())
			.isGreaterThan(1);
	}

	/** AC-02: 사진 넉넉한 코스 중 몇박 며칠이 먼저 나온다. */
	@Test
	void 사진_넉넉한_코스_중_몇박_며칠을_먼저_뽑는다() {
		List<MainCoursePicker.Entry> entries = new ArrayList<>(pool(4, 4));
		entries.add(entry(51, 6, 6, 6, 2));
		entries.add(entry(52, 6, 3, 3, 3));

		List<Long> picked = ids(MainCoursePicker.pick(entries, SEEDED));

		assertThat(List.of(picked.get(0), picked.get(2))).containsExactlyInAnyOrder(51L, 52L);
	}

	/** AC-03·04: 한쪽이 바닥나면 다른 쪽에서 채운다. */
	@ParameterizedTest
	@CsvSource({"2, 10, 'true,false,true,false,false,false,false,false'",
		"10, 1, 'true,false,true,true,true,true,true,true'"})
	void 한쪽이_모자라면_다른_쪽에서_채워_8개를_맞춘다(int rich, int others, String expected) {
		List<Long> picked = ids(MainCoursePicker.pick(pool(rich, others), SEEDED));

		assertThat(picked).hasSize(8).doesNotHaveDuplicates();
		assertThat(picked.stream().map(MainCoursePickerTest::isRich).map(String::valueOf).toList())
			.containsExactly(expected.split(","));
	}

	/** AC-05: 후보가 8개 미만이면 있는 만큼, 없으면 빈 목록. */
	@Test
	void 후보가_8개_미만이면_있는_만큼_없으면_빈_목록() {
		assertThat(ids(MainCoursePicker.pick(pool(2, 3), SEEDED))).hasSize(5).doesNotHaveDuplicates();
		assertThat(MainCoursePicker.pick(List.of(), SEEDED)).isEmpty();
	}

	/** AC-06: 공개 사진 3장 이상이고 사진 붙은 방문이 절반 이상(포함)일 때만 사진 넉넉. */
	@ParameterizedTest
	@CsvSource({"4, 2, 3, true", "6, 3, 3, true", "4, 2, 2, false", "7, 3, 5, false", "3, 3, 3, true"})
	void 사진_넉넉함_경계(long visits, long photoVisits, long photos, boolean rich) {
		assertThat(entry(1, visits, photoVisits, photos, 1).rich()).isEqualTo(rich);
	}
}
