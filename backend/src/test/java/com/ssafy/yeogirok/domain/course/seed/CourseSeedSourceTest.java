package com.ssafy.yeogirok.domain.course.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ssafy.yeogirok.support.InMemoryFileStorage;
import com.ssafy.yeogirok.support.TestFileProperties;

/** 묶음 파일 읽기: S3 는 사진 버킷 접두어 아래 바로 있는 bundle-*.json 만, 로컬은 폴더 밖 경로를 막는다(명세 6-2·6-3). */
class CourseSeedSourceTest {

	@Test
	void S3_는_사진_버킷_접두어_아래_묶음_파일만_이름순으로() {
		InMemoryFileStorage storage = new InMemoryFileStorage();
		storage.put("photos", "course-seed/bundle-b2.json", new byte[] {2}, "application/json");
		storage.put("photos", "course-seed/bundle-b1.json", new byte[] {1}, "application/json");
		storage.put("photos", "course-seed/photos/p1.jpg", new byte[] {3}, "image/jpeg");
		storage.put("photos", "course-seed/old/bundle-b0.json", new byte[] {0}, "application/json");
		storage.put("photos", "record-photo/bundle-x.json", new byte[] {9}, "application/json");
		CourseSeedSource source = CourseSeedSource.of(
			new CourseSeedProperties(true, CourseSeedProperties.Source.S3, "course-seed", null, null, null), storage,
			TestFileProperties.VALUE);

		assertThat(source.bundles()).containsExactly("bundle-b1.json", "bundle-b2.json");
		assertThat(source.read("photos/p1.jpg", 10)).containsExactly(3);
	}

	@Test
	void 로컬은_폴더_밖_경로와_크기_초과를_막는다(@TempDir Path dir) throws Exception {
		Files.write(dir.resolve("bundle-b1.json"), new byte[] {1, 2, 3});
		Files.writeString(dir.resolve("note.txt"), "x");
		CourseSeedSource source = CourseSeedSource.of(
			new CourseSeedProperties(true, CourseSeedProperties.Source.LOCAL, dir.toString(), null, null, null), null,
			TestFileProperties.VALUE);

		assertThat(source.bundles()).containsExactly("bundle-b1.json");
		assertThatThrownBy(() -> source.read("../outside.json", 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> source.read("bundle-b1.json", 2)).isInstanceOf(IllegalArgumentException.class);
	}
}
