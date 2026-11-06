package com.ssafy.yeogirok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** AC-03 크기 경계(한도와 같으면 허용, 1바이트 넘으면 M001), 형식 M002. */
class FilePolicyTest {

	static final FileProperties PROPERTIES = new FileProperties("ap-northeast-2", "photos", "receipts",
		Duration.ofMinutes(10), Duration.ofMinutes(15),
		Map.of(FilePurpose.TIP_PHOTO, DataSize.ofMegabytes(20), FilePurpose.RECORD_PHOTO, DataSize.ofMegabytes(20),
			FilePurpose.RECEIPT, DataSize.ofMegabytes(10)));

	final FilePolicy policy = new FilePolicy(PROPERTIES);

	@ParameterizedTest
	@CsvSource({"TIP_PHOTO, 20971520", "RECORD_PHOTO, 20971520", "RECEIPT, 10485760"})
	void 한도와_같은_크기는_허용하고_1바이트_넘으면_M001(FilePurpose purpose, long limit) {
		assertThatCode(() -> policy.requireSize(purpose, limit)).doesNotThrowAnyException();
		assertThatThrownBy(() -> policy.requireSize(purpose, limit + 1))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.FILE_TOO_LARGE));
	}

	@Test
	void JPEG_PNG_WEBP_가_아니면_M002() {
		assertThat(policy.requireFormat("image/png")).isEqualTo(ImageFormat.PNG);
		assertThatThrownBy(() -> policy.requireFormat("image/heic"))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.FILE_TYPE_NOT_SUPPORTED));
	}

	@Test
	void 용도로_버킷을_고른다() {
		assertThat(PROPERTIES.bucketFor(FilePurpose.RECEIPT)).isEqualTo("receipts");
		assertThat(PROPERTIES.bucketFor(FilePurpose.TIP_PHOTO)).isEqualTo("photos");
		assertThat(PROPERTIES.bucketFor(FilePurpose.RECORD_PHOTO)).isEqualTo("photos");
	}
}
