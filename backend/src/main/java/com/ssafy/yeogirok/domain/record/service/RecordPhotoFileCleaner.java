package com.ssafy.yeogirok.domain.record.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * API-089 로 지운 기록 사진의 파일을 DB 커밋 뒤에 지운다(명세 5절 13). 커밋 전에 지우면 롤백돼도 파일이 사라진다.
 * 실패하면 로그만 남긴다. 저장소와 DB 를 대조하는 안전망 정리는 후속 작업이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordPhotoFileCleaner {

	private final FileStorage fileStorage;
	private final FileProperties fileProperties;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void deleteFile(RecordPhotoDeleted event) {
		try {
			fileStorage.delete(fileProperties.bucketFor(FilePurpose.RECORD_PHOTO), event.imagePath());
		} catch (RuntimeException e) {
			log.warn("기록 사진 파일을 지우지 못했습니다 imageId={} path={}", event.imageId(), event.imagePath(), e);
		}
	}

	/** 지운 기록 사진. imagePath 는 서버가 저장한 확정 경로다(사용자가 보낸 key 가 아니다). */
	public record RecordPhotoDeleted(Long imageId, String imagePath) {
	}
}
