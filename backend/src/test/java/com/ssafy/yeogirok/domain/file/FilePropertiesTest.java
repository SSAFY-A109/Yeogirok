package com.ssafy.yeogirok.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** AC-01 설정 바인딩. MB 는 1,048,576 바이트, 값이 빠지면 시작할 때 실패한다. */
class FilePropertiesTest {

	static Map<String, String> complete() {
		Map<String, String> values = new HashMap<>();
		values.put("yeogirok.file.region", "ap-northeast-2");
		values.put("yeogirok.file.photo-bucket", "photos");
		values.put("yeogirok.file.receipt-bucket", "receipts");
		values.put("yeogirok.file.upload-url-ttl", "10m");
		values.put("yeogirok.file.download-url-ttl", "15m");
		values.put("yeogirok.file.receipt-retention", "24h");
		values.put("yeogirok.file.max-size.TIP_PHOTO", "20MB");
		values.put("yeogirok.file.max-size.RECORD_PHOTO", "20MB");
		values.put("yeogirok.file.max-size.RECEIPT", "10MB");
		return values;
	}

	static FileProperties bind(Map<String, String> values) {
		return new Binder(new MapConfigurationPropertySource(values))
			.bindOrCreate("yeogirok.file", FileProperties.class);
	}

	@Test
	void MB_는_1048576바이트로_읽는다() {
		FileProperties properties = bind(complete());

		assertThat(properties.maxBytesOf(FilePurpose.TIP_PHOTO)).isEqualTo(20L * 1_048_576);
		assertThat(properties.maxBytesOf(FilePurpose.RECEIPT)).isEqualTo(10L * 1_048_576);
		assertThat(properties.uploadUrlTtl()).hasMinutes(10);
	}

	@Test
	void 버킷이_없으면_실패한다() {
		Map<String, String> values = complete();
		values.remove("yeogirok.file.receipt-bucket");

		assertThatThrownBy(() -> bind(values)).isInstanceOf(BindException.class)
			.rootCause().hasMessageContaining("receipt-bucket");
	}

	@Test
	void 용도별_크기가_하나라도_없으면_실패한다() {
		Map<String, String> values = complete();
		values.remove("yeogirok.file.max-size.RECEIPT");

		assertThatThrownBy(() -> bind(values)).isInstanceOf(BindException.class)
			.rootCause().hasMessageContaining("max-size");
	}
}
