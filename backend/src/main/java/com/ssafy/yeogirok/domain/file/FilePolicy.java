package com.ssafy.yeogirok.domain.file;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 용도별 형식·크기 검사. API-012 는 요청 메타데이터, API-086 은 실제 객체로 같은 기준을 적용한다.
 */
@Component
@RequiredArgsConstructor
public class FilePolicy {

	private final FileProperties properties;

	/** @throws BusinessException JPEG·PNG·WEBP 가 아니면 M002 */
	public ImageFormat requireFormat(String mimeType) {
		return ImageFormat.fromMimeType(mimeType)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_TYPE_NOT_SUPPORTED));
	}

	/** @throws BusinessException 용도별 최대 크기를 넘으면 M001(같은 크기는 허용) */
	public void requireSize(FilePurpose purpose, long sizeBytes) {
		if (sizeBytes > properties.maxBytesOf(purpose)) {
			throw new BusinessException(ExceptionType.FILE_TOO_LARGE);
		}
	}
}
