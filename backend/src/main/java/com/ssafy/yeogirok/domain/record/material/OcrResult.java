package com.ssafy.yeogirok.domain.record.material;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * OCR 서버 응답에서 쓰는 값. 형식이 틀린 날짜·시각은 null. status 는 받지 않는다(v3 에서 주소 완전성까지 뜻이 바뀜).
 *
 * @param address         v3 주소. 건물번호까지 읽으면 끝에 붙고, 도로명까지만이면 도로명까지, 지번만이면 시·도 + 시·군·구
 *                        (명세 {@code specs/draft-merge.md} 1절). 응답에 없거나 비었으면 null
 * @param addressComplete 건물번호까지 읽었는지. address 가 null 이면 늘 false(v3 규격과 같게)
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
