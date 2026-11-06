package com.ssafy.yeogirok.domain.file;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 파일 저장소 설정. 값이 없거나 0 이하면 앱이 시작할 때 실패한다. AWS 자격 증명은 여기 두지 않는다(SDK 기본 자격 증명).
 *
 * @param region         S3 리전
 * @param photoBucket    사진 버킷(TIP_PHOTO·RECORD_PHOTO)
 * @param receiptBucket  영수증 버킷(RECEIPT)
 * @param uploadUrlTtl   Presigned PUT URL 과 미완료 업로드 세션의 수명(11/3 결정 10분)
 * @param downloadUrlTtl Presigned GET URL 수명(11/3 결정 15분)
 * @param receiptRetention 영수증 원본 최대 보관 기간. 012 최초 발급 시각부터 센다(노션 API-012 24시간)
 * @param maxSize        용도별 파일당 최대 크기. 같은 크기는 허용한다. {@code 20MB} 는 20 × 1,048,576 바이트다(DataSize 는 1MB = 1,048,576)
 */
@ConfigurationProperties("yeogirok.file")
public record FileProperties(
	String region,
	String photoBucket,
	String receiptBucket,
	Duration uploadUrlTtl,
	Duration downloadUrlTtl,
	Duration receiptRetention,
	Map<FilePurpose, DataSize> maxSize
) {

	public FileProperties {
		requireText(region, "region");
		requireText(photoBucket, "photo-bucket");
		requireText(receiptBucket, "receipt-bucket");
		requirePositive(uploadUrlTtl, "upload-url-ttl");
		requirePositive(downloadUrlTtl, "download-url-ttl");
		requirePositive(receiptRetention, "receipt-retention");
		if (maxSize == null || !maxSize.keySet().containsAll(Arrays.asList(FilePurpose.values()))
			|| maxSize.values().stream().anyMatch(size -> size.toBytes() <= 0)) {
			throw new IllegalArgumentException("yeogirok.file.max-size 는 모든 용도에 0보다 큰 값이 필요합니다.");
		}
		maxSize = Map.copyOf(maxSize);
	}

	public String bucketFor(FilePurpose purpose) {
		return purpose.isReceipt() ? receiptBucket : photoBucket;
	}

	public long maxBytesOf(FilePurpose purpose) {
		return maxSize.get(purpose).toBytes();
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("yeogirok.file.%s 값이 필요합니다.".formatted(name));
		}
	}

	private static void requirePositive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("yeogirok.file.%s 값이 필요합니다(0보다 커야 합니다).".formatted(name));
		}
	}
}
