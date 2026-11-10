package com.ssafy.yeogirok.domain.record.draft;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.material.MaterialTestConfig;
import com.ssafy.yeogirok.domain.record.service.RecordAccess;

/**
 * 통합 초안 서비스 @DataJpaTest 용 빈. 영수증 자료 빈(실제 Redis·작업 실행기)에 초안 생성 빈을 더한다. EXIF 는 가짜 리더가 읽는다:
 * S3 mock 이 사진 경로를 바이트로 돌려주면 그 경로에 넣어 둔 값을 준다.
 */
@TestConfiguration
@Import({MaterialTestConfig.class, RecordAccess.class, RecordDraftGenerator.class, RecordDraftService.class})
public class RecordDraftTestConfig {

	@Bean
	FakePhotoMetadataReader photoMetadataReader() {
		return new FakePhotoMetadataReader();
	}

	public static class FakePhotoMetadataReader implements PhotoMetadataReader {

		private final Map<String, PhotoMetadata> byPath = new HashMap<>();

		public void put(String imagePath, PhotoMetadata metadata) {
			byPath.put(imagePath, metadata);
		}

		public void clear() {
			byPath.clear();
		}

		@Override
		public Optional<PhotoMetadata> read(byte[] head) {
			return Optional.ofNullable(byPath.get(new String(head, StandardCharsets.UTF_8)));
		}
	}
}
