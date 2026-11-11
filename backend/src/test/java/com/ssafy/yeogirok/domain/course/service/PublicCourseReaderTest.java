package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.SIGNED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.category.entity.TravelCategoryEntity;
import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.CourseDay;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.course.dto.PublicPhoto;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.entity.VisitStory;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.support.TestUsers;

/** 178 공개 대상 조회·selectionHash·작성자·공개 선택 저장. */
@DataJpaTest
@Import(CourseTestConfig.class)
class PublicCourseReaderTest {

	static final long ME = 9301L;
	static final long OTHER = 9302L;

	@Autowired
	PublicCourseReader reader;
	@Autowired
	CourseAuthors courseAuthors;
	@Autowired
	CourseSelection courseSelection;
	@Autowired
	TestEntityManager em;

	CourseFixture fixture;
	TravelRoom room;
	TravelRecord record;
	ActualVisit first;
	ActualVisit second;
	VisitImage linked;
	VisitImage unlinked;

	@BeforeEach
	void setUp() {
		fixture = new CourseFixture(em);
		room = fixture.room(DAY1, OTHER, ME);
		record = fixture.record(room);
		Long place = fixture.place("경포대");
		em.getEntityManager().createNativeQuery("update places set category_code = 'NATURE' where id = ?1")
			.setParameter(1, place).executeUpdate();
		em.persist(new TravelCategoryEntity("NATURE", "자연 힐링"));
		second = fixture.visit(record.getId(), place, DAY1.plusDays(1), 1);
		first = fixture.visit(record.getId(), place, DAY1, 1);
		linked = fixture.photo(record.getId(), first.getId(), 1);
		unlinked = fixture.photo(record.getId(), null, 0);
		em.persist(VisitStory.write(first.getId(), ME, "비공개 이야기", NOW));
		em.flush();
		em.clear();
	}

	private String hash(int version, String title, String description, boolean includePhotos) {
		return SelectionHash.of(record.getId(), version, title, description, includePhotos,
			reader.snapshot(record.getId()));
	}

	@Nested
	class 공개_대상 {

		@Test
		void 방문은_날짜_순서대로_일차로_묶고_연결된_사진만_넣는다() {
			List<CourseDay> days = reader.days(reader.snapshot(record.getId()), DAY1, PhotoScope.LINKED);

			assertThat(days).extracting(CourseDay::day).containsExactly(1, 2);
			CourseDay.Item item = days.getFirst().items().getFirst();
			assertThat(item.visitId()).isEqualTo(first.getId());
			assertThat(item.order()).isEqualTo(1);
			assertThat(item.place().name()).isEqualTo("경포대");
			assertThat(item.place().categoryName()).isEqualTo("자연 힐링");
			assertThat(item.photos()).extracting(PublicPhoto::imageId).containsExactly(linked.getId());
			assertThat(item.photos().getFirst().url()).startsWith(SIGNED);
			assertThat(item.photos().getFirst().attribution()).isNull();
			assertThat(days.get(1).items().getFirst().photos()).isEmpty();
		}

		@Test
		void 사진을_빼거나_공개_선택된_것만_넣을_수_있다() {
			CourseSnapshot snapshot = reader.snapshot(record.getId());

			assertThat(reader.days(snapshot, DAY1, PhotoScope.NONE).getFirst().items().getFirst().photos()).isEmpty();
			assertThat(reader.days(snapshot, DAY1, PhotoScope.PUBLIC_SELECTED).getFirst().items().getFirst().photos())
				.isEmpty();
		}

		@Test
		void 방_시작일이_없거나_첫_방문보다_늦으면_첫_방문_날짜가_1일차() {
			CourseSnapshot snapshot = reader.snapshot(record.getId());

			assertThat(reader.days(snapshot, null, PhotoScope.NONE)).extracting(CourseDay::day).containsExactly(1, 2);
			assertThat(reader.days(snapshot, DAY1.plusDays(1), PhotoScope.NONE)).extracting(CourseDay::day)
				.containsExactly(1, 2);
			assertThat(reader.days(snapshot, DAY1.minusDays(2), PhotoScope.NONE)).extracting(CourseDay::day)
				.containsExactly(3, 4);
		}

		@Test
		void 방문이_없으면_빈_목록() {
			TravelRecord empty = fixture.record(fixture.room(null, 9399L));

			assertThat(reader.days(reader.snapshot(empty.getId()), null, PhotoScope.LINKED)).isEmpty();
		}

		@Test
		void 미연결_사진은_원본에_없다() {
			assertThat(reader.snapshot(record.getId()).linkedPhotos()).extracting(VisitImage::getId)
				.containsExactly(linked.getId()).doesNotContain(unlinked.getId());
		}
	}

	@Nested
	class 선택_해시 {

		@Test
		void 같은_입력이면_같고_64자_16진수() {
			assertThat(hash(1, "강릉", "", true)).isEqualTo(hash(1, "강릉", "", true)).matches("[0-9a-f]{64}");
		}

		@Test
		void 제목_설명_사진포함_version_중_하나만_바뀌어도_다르다() {
			String base = hash(1, "강릉", "설명", true);

			assertThat(List.of(hash(2, "강릉", "설명", true), hash(1, "강릉 ", "설명", true),
				hash(1, "강릉", "설명 ", true), hash(1, "강릉", "설명", false))).doesNotContain(base).doesNotHaveDuplicates();
		}

		@Test
		void 제목과_설명의_경계가_바뀌면_다르다() {
			assertThat(hash(1, "ab", "c", true)).isNotEqualTo(hash(1, "a", "bc", true));
		}

		@Test
		void 방문_순서가_바뀌면_다르다() {
			String before = hash(1, "강릉", "", false);
			em.find(ActualVisit.class, second.getId()).moveTo(2, NOW);
			em.flush();
			em.clear();

			assertThat(hash(1, "강릉", "", false)).isNotEqualTo(before);
		}

		@Test
		void 사진은_포함할_때만_해시에_들어간다() {
			String withPhotos = hash(1, "강릉", "", true);
			String withoutPhotos = hash(1, "강릉", "", false);
			fixture.photo(record.getId(), second.getId(), 1);
			em.flush();
			em.clear();

			assertThat(hash(1, "강릉", "", true)).isNotEqualTo(withPhotos);
			assertThat(hash(1, "강릉", "", false)).isEqualTo(withoutPhotos);
		}
	}

	@Nested
	class 작성자 {

		@Test
		void 현재_참여자를_userId_순으로_탈퇴자와_나간_사람은_뺀다() {
			TestUsers.insert(em.getEntityManager(), 9303L, "WITHDRAWN");
			em.persist(RoomMember.joined(room.getId(), 9303L, NOW));
			TestUsers.insertActive(em.getEntityManager(), 9300L);
			em.persist(RoomMember.joined(room.getId(), 9300L, NOW)).leave(NOW);
			em.getEntityManager().createNativeQuery("update users set profile_image_url = 'https://k.test/me.png'"
				+ " where id = ?1").setParameter(1, ME).executeUpdate();
			em.flush();

			assertThat(courseAuthors.of(room.getId())).containsExactly(new PublicAuthor(ME, "https://k.test/me.png"),
				new PublicAuthor(OTHER, null));
		}

		@Test
		void 여러_방을_한_번에_읽는다() {
			TravelRoom another = fixture.room(null, 9310L);
			em.flush();

			assertThat(courseAuthors.byRoom(List.of(room.getId(), another.getId())))
				.containsOnlyKeys(room.getId(), another.getId());
			assertThat(courseAuthors.byRoom(List.of())).isEmpty();
		}
	}

	@Nested
	class 공개_선택_저장 {

		@Test
		void 사진_포함이면_연결된_사진만_공개하고_방문은_모두_공개한다() {
			em.getEntityManager().createNativeQuery("update actual_visits set is_public_selected = false")
				.executeUpdate();

			assertThat(courseSelection.save(record.getId(), true)).isTrue();
			em.clear();

			assertThat(em.find(ActualVisit.class, first.getId()).isPublicSelected()).isTrue();
			assertThat(em.find(ActualVisit.class, second.getId()).isPublicSelected()).isTrue();
			assertThat(em.find(VisitImage.class, linked.getId()).isPublicSelected()).isTrue();
			assertThat(em.find(VisitImage.class, unlinked.getId()).isPublicSelected()).isFalse();
		}

		@Test
		void 사진_제외면_모든_사진을_해제하고_바뀐_것이_없으면_false() {
			courseSelection.save(record.getId(), true);

			assertThat(courseSelection.save(record.getId(), false)).isTrue();
			assertThat(courseSelection.save(record.getId(), false)).isFalse();
			em.clear();
			assertThat(em.find(VisitImage.class, linked.getId()).isPublicSelected()).isFalse();
		}
	}

	@Test
	void 코스_변경은_바뀐_것이_있을_때만_version을_올리고_완성_상태는_그대로() {
		TravelRecord found = em.find(TravelRecord.class, record.getId());
		found.complete(NOW);

		assertThat(found.changeCourse("강릉 여행", "", null, false, NOW)).isFalse();
		assertThat(found.changeCourse("강릉 여행", "", NOW, false, NOW)).isTrue();
		assertThat(found.getVersion()).isEqualTo(3);
		assertThat(found.getStatus()).isEqualTo(RecordStatus.COMPLETED);
		assertThat(found.changeCourse("강릉 여행", "", NOW, true, NOW)).isTrue();
		assertThat(found.getVersion()).isEqualTo(4);
	}
}
