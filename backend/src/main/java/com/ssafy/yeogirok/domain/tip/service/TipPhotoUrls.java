package com.ssafy.yeogirok.domain.tip.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.tip.entity.ArTipImage;
import com.ssafy.yeogirok.domain.tip.repository.ArTipImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 팁 사진 Presigned GET URL 발급(API-048·090·013). 권한·현장 조건은 부르는 쪽이 먼저 확인한다.
 * 서명 URL 은 저장하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TipPhotoUrls {

	private final ArTipImageRepository imageRepository;
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

	/** @param expiresAt URL 만료 시각 */
	public record IssuedPhoto(Long imageId, String url, Instant expiresAt) {
	}
}
