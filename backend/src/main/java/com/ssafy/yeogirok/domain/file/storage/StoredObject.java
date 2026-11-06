package com.ssafy.yeogirok.domain.file.storage;

/** S3 객체의 실제 크기와 ETag(따옴표 포함, 조건부 요청에 그대로 쓴다). */
public record StoredObject(long sizeBytes, String eTag) {
}
