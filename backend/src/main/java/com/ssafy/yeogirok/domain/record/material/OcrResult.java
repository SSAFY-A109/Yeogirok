package com.ssafy.yeogirok.domain.record.material;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * OCR 서버 응답에서 쓰는 값. 형식이 틀린 날짜·시각은 null. status 는 받지 않는다(v3 에서 주소 완전성까지 뜻이 바뀜).
 *
 * @param address         v3 주소(시·도~도로명, 건물번호 없음). 응답에 없거나 비었으면 null
 * @param addressComplete 원문에서 건물번호까지 읽었는지. address 가 null 이면 늘 false(v3 규격과 같게)
 */
public record OcrResult(String merchantName, LocalDate paidDate, LocalTime paidTime, String address,
	boolean addressComplete) {

	public OcrResult {
		addressComplete = addressComplete && address != null;
	}

	/** 주소 칸이 없는 응답(v3 이전). */
	public OcrResult(String merchantName, LocalDate paidDate, LocalTime paidTime) {
		this(merchantName, paidDate, paidTime, null, false);
	}
}
