package com.ssafy.yeogirok.domain.file.dto;

import com.ssafy.yeogirok.domain.file.FilePurpose;

/**
 * API-012 요청을 읽은 값. 멱등키 요청 해시도 이 값으로 계산한다.
 *
 * @param fileName 원본 파일명. 저장 key 로 쓰지 않는다
 */
public record UploadCreateCommand(FilePurpose purpose, String fileName, String contentType, long sizeBytes,
	Long roomId, Long recordId) {
}
