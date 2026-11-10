package com.ssafy.yeogirok.domain.record.material;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * 영수증 OCR 서버 설정. 주소는 환경 변수 {@code OCR_BASE_URL}(서버 내부망 {@code http://ai:8000}). 비어 있어도 서버는 뜨고
 * OCR 작업만 바로 실패한다(C014, 다시 시도 가능. 명세 {@code specs/record-api-2.md} 5절 4).
 *
 * @param readTimeout 응답 제한 시간
 * @param slotWait    OCR 차례(한 번에 한 건)를 기다리는 최대 시간. 응답 제한과 따로 둔다(앞 건이 응답 제한 가까이 걸려도 뒤 건이
 *                    차례를 받게). 차례 대기 + 호출이 작업 실행 제한(2분) 안에 들어와야 한다(PR #84 리뷰)
 */
@Validated
@ConfigurationProperties("yeogirok.ocr")
public record OcrProperties(String baseUrl, @NotNull Duration connectTimeout, @NotNull Duration readTimeout,
	@NotNull Duration slotWait) {

	/** 끝의 "/" 를 지워 "//ocr/receipt" 가 되지 않게 한다(PR #84 리뷰). */
	public OcrProperties {
		baseUrl = baseUrl == null ? null : baseUrl.strip().replaceAll("/+$", "");
	}

	public boolean hasBaseUrl() {
		return baseUrl != null && !baseUrl.isBlank();
	}
}
