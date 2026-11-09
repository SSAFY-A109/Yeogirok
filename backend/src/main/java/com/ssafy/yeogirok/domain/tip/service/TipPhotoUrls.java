package com.ssafy.yeogirok.domain.tip.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.dto.FileAccessResponse;
import com.ssafy.yeogirok.domain.file.service.FileUrlIssuer;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.repository.ArTipImageRepository;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 팁 사진 Presigned GET URL 발급(API-048·090·013). 048·090 은 권한·현장 조건을 부르는 쪽이 먼저 확인한다.
 * 서명 URL 은 저장하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TipPhotoUrls {

	private final ArTipRepository arTipRepository;
	private final ArTipImageRepository imageRepository;
	private final FileUrlIssuer fileUrlIssuer;
	private final FileStorage fileStorage;
	private final FileProperties fileProperties;
	private final Clock clock;

	/** @return 팁에 사진이 없으면 empty */
	public Optional<IssuedPhoto> issueFor(Long tipId) {
		return imageRepository.findByTipId(tipId).map(this::issue);
	}

	public IssuedPhoto issue(ArTipImage image) {
		Duration validFor = fileProperties.downloadUrlTtl();
		String url = fileStorage.presignGet(fileProperties.bucketFor(FilePurpose.TIP_PHOTO), image.getImagePath(),
			validFor);
		return new IssuedPhoto(image.getId(), url, clock.instant().plus(validFor));
	}

	/**
	 * API-013 작성자용 팁 사진 경로. 위치 증거 없이 원격 발급한다(다른 사람은 048 에서만).
	 * 팁이 없거나 삭제됐거나 사진이 그 팁 것이 아니면 M004, 작성자가 아니면 M005(노션 013).
	 */
	public FileAccessResponse accessForAuthor(Long userId, Long tipId, Long imageId) {
		ArTip tip = arTipRepository.findByIdAndDeletedAtIsNull(tipId)
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		if (!tip.getAuthorId().equals(userId)) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
		ArTipImage image = imageRepository.findById(imageId)
			.filter(found -> found.getTipId().equals(tipId))
			.orElseThrow(() -> new BusinessException(ExceptionType.FILE_NOT_FOUND));
		return fileUrlIssuer.issue(fileProperties.bucketFor(FilePurpose.TIP_PHOTO), image.getImagePath(),
			clock.instant(), fileProperties.downloadUrlTtl());
	}

	/** @param expiresAt URL 만료 시각 */
	public record IssuedPhoto(Long imageId, String url, Instant expiresAt) {
	}
}
