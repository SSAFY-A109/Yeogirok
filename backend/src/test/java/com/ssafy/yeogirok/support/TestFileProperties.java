package com.ssafy.yeogirok.support;

import java.time.Duration;
import java.util.Map;

import org.springframework.util.unit.DataSize;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;

/** application.yaml 과 같은 파일 설정(URL 10분·15분, 영수증 24시간, 20MB·20MB·10MB). 버킷 이름은 테스트용이다. */
public final class TestFileProperties {

	public static final FileProperties VALUE = new FileProperties("ap-northeast-2", "photos", "receipts",
		Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofHours(24),
		Map.of(FilePurpose.TIP_PHOTO, DataSize.ofMegabytes(20), FilePurpose.RECORD_PHOTO, DataSize.ofMegabytes(20),
			FilePurpose.RECEIPT, DataSize.ofMegabytes(10)));

	private TestFileProperties() {
	}
}
