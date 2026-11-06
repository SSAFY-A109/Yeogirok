package com.ssafy.yeogirok.domain.file.storage;

/** S3 객체의 실제 크기, ETag(따옴표 포함, 조건부 요청에 그대로 쓴다), 올릴 때 서명으로 고정한 Content-Type. */
public record StoredObject(long sizeBytes, String eTag, String contentType) {
}
