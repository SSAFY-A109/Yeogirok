package com.ssafy.yeogirok.domain.record.dto;

/** API-062 검사를 마친 요청. PAYMENT 도 영수증과 같은 OCR 흐름이고 자료 type 만 다르다(명세 specs/record-api-2.md 5절 9). */
public record MaterialLinkCommand(Type type, String uploadId) {

	public enum Type { RECEIPT, PAYMENT }
}
