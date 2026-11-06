package com.ssafy.yeogirok.domain.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Presigned URL 서명. 서명은 네트워크 없이 만들어지므로 가짜 자격 증명으로 실제 SDK 를 쓴다(S3 는 부르지 않는다).
 */
class FileStorageTest {

	static S3Presigner presigner(StaticCredentialsProvider credentials) {
		return S3Presigner.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build();
	}

	@Test
	void PUT_URL_은_지정한_key_와_Content_Type_에만_쓰도록_서명된다() {
		FileStorage storage = new FileStorage(
			presigner(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATESTONLY", "test-secret"))));

		String url = storage.presignPut("photos", "tmp/abc", "image/png", Duration.ofMinutes(10));

		assertThat(url).startsWith("https://photos.s3.ap-northeast-2.amazonaws.com/tmp/abc?")
			.contains("X-Amz-Expires=600")
			.contains("X-Amz-SignedHeaders=content-type%3Bhost");
	}

	@Test
	void 자격_증명을_찾지_못하면_M012() {
		FileStorage storage = new FileStorage(S3Presigner.builder().region(Region.AP_NORTHEAST_2)
			.credentialsProvider(() -> {
				throw SdkClientException.create("자격 증명 없음");
			}).build());

		assertThatThrownBy(() -> storage.presignPut("photos", "tmp/abc", "image/png", Duration.ofMinutes(10)))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.FILE_STORAGE_UNAVAILABLE));
	}
}
