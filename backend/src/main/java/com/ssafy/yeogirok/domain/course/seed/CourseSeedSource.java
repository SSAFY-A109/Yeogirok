package com.ssafy.yeogirok.domain.course.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;

/** 묶음 파일·사진을 읽는다. S3 는 사진 버킷의 {@code location} 접두어 아래, 로컬은 {@code location} 폴더 아래. */
interface CourseSeedSource {

	String BUNDLE_PREFIX = "bundle-";
	String BUNDLE_SUFFIX = ".json";

	/** @return 묶음 파일 상대 경로(이름순) */
	List<String> bundles();

	/** @throws RuntimeException 없거나 maxBytes 를 넘으면 */
	byte[] read(String relativePath, long maxBytes);

	static CourseSeedSource of(CourseSeedProperties properties, FileStorage storage, FileProperties files) {
		return properties.source() == CourseSeedProperties.Source.LOCAL ? new Local(Path.of(properties.location()))
			: new S3(storage, files.bucketFor(FilePurpose.RECORD_PHOTO), prefix(properties.location()));
	}

	private static String prefix(String location) {
		return location.endsWith("/") ? location : location + "/";
	}

	static boolean isBundle(String name) {
		return name.startsWith(BUNDLE_PREFIX) && name.endsWith(BUNDLE_SUFFIX);
	}

	record S3(FileStorage storage, String bucket, String prefix) implements CourseSeedSource {

		@Override
		public List<String> bundles() {
			return storage.listKeysModifiedBefore(bucket, prefix, Instant.MAX).stream()
				.map(key -> key.substring(prefix.length()))
				.filter(name -> !name.contains("/") && isBundle(name))
				.sorted()
				.toList();
		}

		@Override
		public byte[] read(String relativePath, long maxBytes) {
			return storage.readAll(bucket, prefix + relativePath, maxBytes);
		}
	}

	record Local(Path root) implements CourseSeedSource {

		@Override
		public List<String> bundles() {
			try (Stream<Path> files = Files.list(root)) {
				return files.map(path -> path.getFileName().toString()).filter(CourseSeedSource::isBundle).sorted().toList();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public byte[] read(String relativePath, long maxBytes) {
			Path path = root.resolve(relativePath).normalize();
			if (!path.startsWith(root.normalize())) {
				throw new IllegalArgumentException("묶음 폴더 밖 경로입니다.");
			}
			try {
				if (Files.size(path) > maxBytes) {
					throw new IllegalArgumentException("파일이 너무 큽니다.");
				}
				return Files.readAllBytes(path);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
