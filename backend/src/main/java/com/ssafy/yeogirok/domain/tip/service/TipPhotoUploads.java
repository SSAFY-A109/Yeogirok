package com.ssafy.yeogirok.domain.tip.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.session.UploadSession;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.file.session.UploadStatus;
import com.ssafy.yeogirok.domain.tip.repository.ArTipImageRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 팁에 붙일 사진 업로드 검사(API-049·054). */
@Component
@RequiredArgsConstructor
public class TipPhotoUploads {

	private final UploadSessionStore uploadSessionStore;
	private final ArTipImageRepository arTipImageRepository;

	/**
	 * 사진 0~1장(2장 이상 C003). 세션 없음 M004 → 본인 것 아님 M005 → READY 아님 M006 → TIP_PHOTO 아님 M007 →
	 * 이미 다른 팁에 붙은 사진 M005(11/8 결정).
	 * @return 확정 경로. 사진이 없으면 null
	 */
	public String confirmedPath(Long userId, List<String> uploadIds) {
		if (uploadIds.size() > 1) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "uploadIds: 사진은 최대 1장입니다.");
		}
		if (uploadIds.isEmpty()) {
			return null;
		}
		UploadSession session = uploadSessionStore.findOwned(uploadIds.getFirst(), userId);
		if (session.status() != UploadStatus.READY) {
			throw new BusinessException(ExceptionType.FILE_NOT_UPLOADED);
		}
		if (session.purpose() != FilePurpose.TIP_PHOTO) {
			throw new BusinessException(ExceptionType.FILE_PURPOSE_MISMATCH);
		}
		if (arTipImageRepository.existsByImagePath(session.confirmedKey())) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED, "이미 다른 팁에 붙인 사진입니다.");
		}
		return session.confirmedKey();
	}
}
