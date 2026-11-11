package com.ssafy.yeogirok.domain.record.material;

import java.time.LocalDate;
import java.time.LocalTime;

/** OCR 서버 응답에서 쓰는 값. 형식이 틀린 날짜·시각은 null. 주소·상태(status)는 받지 않는다. */
public record OcrResult(String merchantName, LocalDate paidDate, LocalTime paidTime) {
}
