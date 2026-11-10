package com.ssafy.yeogirok.domain.file.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.dto.FileAccessResponse;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.file.storage.StoredObject;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** API-013 응답 발급(기록 사진·영수증·팁 사진 공통). 권한은 부르는 쪽이 먼저 확인한다. */
@Component
@RequiredArgsConstructor
public class FileUrlIssuer {

	private final FileStorage fileStorage;

	/**
	 * 확정 객체의 GET URL. Content-Type 은 업로드 때 서명으로 고정한 객체 값을 쓴다(이미지 테이블에는 형식 컬럼이 없다).
	 * 객체가 없으면 M004.
	 */
	public FileAccessResponse issue(String bucket, String key, Instant now, Duration validFor) {
		StoredObject object = fileStorage.head(bucket, key)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		return new FileAccessResponse(fileStorage.presignGet(bucket, key, validFor), now.plus(validFor),
			object.contentType());
	}
}
