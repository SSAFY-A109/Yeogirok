package com.ssafy.yeogirok.domain.record.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;

import lombok.RequiredArgsConstructor;

/** 업로드를 마친 기록 사진을 보관함(visit_images)에 넣는다(API-086). */
@Service
@RequiredArgsConstructor
public class RecordPhotoService {

	private final VisitImageRepository visitImageRepository;
	private final Clock clock;

	/**
	 * 같은 파일이 이미 있으면 그 사진을 돌려준다. 저장 뒤 세션 갱신이 실패해 완료를 다시 처리해도 사진이 늘지 않는다
	 * (UNIQUE (record_id, image_path) 도 막는다).
	 *
	 * @return imageId
	 */
	@Transactional
	public Long saveUploaded(Long recordId, Long uploadedBy, String imagePath) {
		return visitImageRepository.findByRecordIdAndImagePath(recordId, imagePath)
			.orElseGet(() -> visitImageRepository.save(VisitImage.unlinked(recordId, uploadedBy, imagePath,
				clock.instant())))
			.getId();
	}
}
