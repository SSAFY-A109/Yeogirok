package com.ssafy.yeogirok.domain.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 호출과 오류 변환. 서명은 네트워크 없이 만들어지므로 가짜 자격 증명으로 실제 SDK 를 쓰고, 나머지 호출은 S3Client 를 대신한다.
 */
class FileStorageTest {

	final S3Client s3Client = mock(S3Client.class);

	FileStorage storageWith(S3Presigner presigner) {
		return new FileStorage(s3Client, presigner);
	}

	FileStorage storage() {
		return storageWith(S3Presigner.builder().region(Region.AP_NORTHEAST_2)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATESTONLY", "test")))
			.build());
	}

	@Test
	void PUT_URL_은_지정한_key_Content_Type_크기에만_쓰도록_서명된다() {
		String url = storage().presignPut("photos", "tmp/abc", "image/png", 1024, Duration.ofMinutes(10));

		assertThat(url).startsWith("https://photos.s3.ap-northeast-2.amazonaws.com/tmp/abc?")
			.contains("X-Amz-Expires=600")
			.contains("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost");
	}

	@Test
	void GET_URL_은_지정한_key_를_정한_시간_동안만_읽도록_서명된다() {
		String url = storage().presignGet("receipts", "receipt/abc", Duration.ofMinutes(15));

		assertThat(url).startsWith("https://receipts.s3.ap-northeast-2.amazonaws.com/receipt/abc?")
			.contains("X-Amz-Expires=900");
	}

	@Test
	void 자격_증명을_찾지_못하면_M012() {
		FileStorage storage = storageWith(S3Presigner.builder().region(Region.AP_NORTHEAST_2)
			.credentialsProvider(() -> {
				throw SdkClientException.create("자격 증명 없음");
			}).build());

		assertError(() -> storage.presignPut("photos", "tmp/abc", "image/png", 1024, Duration.ofMinutes(10)),
			ExceptionType.FILE_STORAGE_UNAVAILABLE);
	}

	@Test
	@SuppressWarnings("unchecked")
	void HEAD_는_크기_ETag_Content_Type_을_주고_없으면_비어_있다() {
		given(s3Client.headObject(any(Consumer.class)))
			.willReturn(HeadObjectResponse.builder().contentLength(12L).eTag("\"e1\"").contentType("image/png").build())
			.willThrow(s3Error(404));

		assertThat(storage().head("photos", "tmp/abc")).contains(new StoredObject(12, "\"e1\"", "image/png"));
		assertThat(storage().head("photos", "tmp/abc")).isEmpty();
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({"412, FILE_VERIFICATION_FAILED", "404, FILE_NOT_UPLOADED", "500, FILE_STORAGE_UNAVAILABLE"})
	@SuppressWarnings("unchecked")
	void 복사_실패는_상태_코드로_나눈다(int status, ExceptionType expected) {
		given(s3Client.copyObject(any(Consumer.class))).willThrow(s3Error(status));

		assertError(() -> storage().copyIfMatch("photos", "tmp/abc", "tip-photo/abc", "\"e1\""), expected);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 앞부분을_읽는_중_객체가_바뀌었으면_M003_HEAD_장애는_M012() {
		given(s3Client.getObjectAsBytes(any(Consumer.class))).willThrow(s3Error(412));
		given(s3Client.headObject(any(Consumer.class))).willThrow(SdkClientException.create("연결 실패"));

		assertError(() -> storage().readHeader("photos", "tmp/abc", 12, "\"e1\""),
			ExceptionType.FILE_VERIFICATION_FAILED);
		assertError(() -> storage().head("photos", "tmp/abc"), ExceptionType.FILE_STORAGE_UNAVAILABLE);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 목록은_모든_쪽을_넘기며_기준_시각보다_오래된_key_만_준다() {
		Instant cutoff = Instant.parse("2026-11-06T00:00:00Z");
		given(s3Client.listObjectsV2(any(Consumer.class)))
			.willReturn(ListObjectsV2Response.builder().nextContinuationToken("next").contents(
				S3Object.builder().key("tmp/old").lastModified(cutoff.minusSeconds(1)).build(),
				S3Object.builder().key("tmp/edge").lastModified(cutoff).build()).build())
			.willReturn(ListObjectsV2Response.builder().contents(
				S3Object.builder().key("receipt/old").lastModified(cutoff.minusSeconds(3600)).build()).build());

		assertThat(storage().listKeysModifiedBefore("receipts", cutoff)).containsExactly("tmp/old", "receipt/old");
	}

	@Test
	@SuppressWarnings("unchecked")
	void 목록을_prefix로_좁힐_수_있다() {
		given(s3Client.listObjectsV2(any(Consumer.class))).willReturn(ListObjectsV2Response.builder().build());

		storage().listKeysModifiedBefore("photos", "tip-photo/", Instant.parse("2026-11-06T00:00:00Z"));

		ArgumentCaptor<Consumer<ListObjectsV2Request.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
		verify(s3Client).listObjectsV2(request.capture());
		ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder();
		request.getValue().accept(builder);
		assertThat(builder.build().prefix()).isEqualTo("tip-photo/");
		assertThat(builder.build().bucket()).isEqualTo("photos");
	}

	@Test
	@SuppressWarnings("unchecked")
	void 삭제_장애는_M012() {
		given(s3Client.deleteObject(any(Consumer.class))).willThrow(s3Error(500));

		assertError(() -> storage().delete("receipts", "tmp/abc"), ExceptionType.FILE_STORAGE_UNAVAILABLE);
	}

	private static S3Exception s3Error(int status) {
		return (S3Exception)S3Exception.builder().statusCode(status).message("status " + status).build();
	}

	@SuppressWarnings("unchecked")
	private void objectIs(byte[] body, Long contentLength) {
		given(s3Client.getObject(any(Consumer.class))).willAnswer(invocation -> new ResponseInputStream<>(
			GetObjectResponse.builder().contentLength(contentLength).build(),
			AbortableInputStream.create(new ByteArrayInputStream(body))));
	}

	@Test
	void 전체_읽기는_상한까지_받고_넘으면_M001_크기_값이_없어도_실제_바이트로_막는다() {
		objectIs(new byte[] {1, 2, 3}, 3L);
		assertThat(storage().readAll("receipts", "receipt/a", 3)).containsExactly(1, 2, 3);

		objectIs(new byte[] {1, 2, 3, 4}, 4L);
		assertError(() -> storage().readAll("receipts", "receipt/a", 3), ExceptionType.FILE_TOO_LARGE);

		objectIs(new byte[] {1, 2, 3, 4}, null);
		assertError(() -> storage().readAll("receipts", "receipt/a", 3), ExceptionType.FILE_TOO_LARGE);

		objectIs(new byte[] {1, 2}, null);
		assertThat(storage().readAll("receipts", "receipt/a", 3)).containsExactly(1, 2);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 전체_읽기에서_객체가_없으면_M004_그_밖은_M012() {
		given(s3Client.getObject(any(Consumer.class))).willThrow(s3Error(404));
		assertError(() -> storage().readAll("receipts", "receipt/a", 3), ExceptionType.FILE_NOT_FOUND);

		given(s3Client.getObject(any(Consumer.class))).willThrow(s3Error(500));
		assertError(() -> storage().readAll("receipts", "receipt/a", 3), ExceptionType.FILE_STORAGE_UNAVAILABLE);
	}

	private static void assertError(Runnable call, ExceptionType expected) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getExceptionType()).isEqualTo(expected));
	}
}
