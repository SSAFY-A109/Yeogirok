package com.ssafy.yeogirok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-02 파일 앞부분 바이트로 JPEG·PNG·WEBP 판별, 나머지는 판별하지 않는다(M002 는 부르는 쪽). */
class ImageFormatTest {

	static Stream<Arguments> 시그니처() {
		return Stream.of(
			Arguments.of(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1), Optional.of(ImageFormat.JPEG)),
			Arguments.of(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D), Optional.of(ImageFormat.PNG)),
			Arguments.of(bytes('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P'), Optional.of(ImageFormat.WEBP)),
			// RIFF 이지만 WEBP 가 아니다(WAV).
			Arguments.of(bytes('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E'), Optional.empty()),
			Arguments.of(bytes('G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0), Optional.empty()),
			// HEIC: ....ftypheic
			Arguments.of(bytes(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'), Optional.empty()),
			Arguments.of(bytes('%', 'P', 'D', 'F', '-', '1', '.', '7', 0, 0, 0, 0), Optional.empty()),
			// 경계: 시그니처보다 짧은 파일
			Arguments.of(bytes(0xFF, 0xD8), Optional.empty()),
			Arguments.of(bytes('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B'), Optional.empty()),
			Arguments.of(new byte[0], Optional.empty()));
	}

	@ParameterizedTest
	@MethodSource("시그니처")
	void 앞부분_바이트로_형식을_판별한다(byte[] header, Optional<ImageFormat> expected) {
		assertThat(ImageFormat.detect(header)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {"image/jpeg", "IMAGE/PNG", "image/webp"})
	void 허용_MIME_은_대소문자를_가리지_않는다(String mimeType) {
		assertThat(ImageFormat.fromMimeType(mimeType)).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = {"image/heic", "image/heif", "image/gif", "application/pdf", "image/jpg",
		"image/jpeg; charset=utf-8", ""})
	void 그_외_MIME_은_찾지_않는다(String mimeType) {
		assertThat(ImageFormat.fromMimeType(mimeType)).isEmpty();
	}

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte)values[i];
		}
		return result;
	}
}
