package com.ssafy.yeogirok.domain.tip.publish;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.tip.repository.ArTipImageRepository;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 팁에 연결되지 않은 사진 정리(파일 API 에서 넘긴 일, 11/9 결정). 확정({@code tip-photo/})된 지 멱등키 보관 기간(24시간)이 지났는데
 * ar_tip_images 에도, 진행·실패한 게시 작업({@code tip-publish:*})·수정안({@code tip-edit:*})에도 없는 파일을 지운다.
 * 업로드 세션도 같은 기간에 끝나 그 뒤로는 새로 붙일 수 없다. 054 로 바뀐 기존 사진도 여기서 지워진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TipPhotoCleanupJob {

	private static final String TIP_PHOTO_PREFIX = FilePurpose.TIP_PHOTO.confirmedKey("");

	private final FileStorage fileStorage;
	private final FileProperties fileProperties;
	private final ArTipImageRepository arTipImageRepository;
	private final TipPublishStore publishStore;
	private final TipEditStore editStore;
	private final IdempotencyProperties idempotencyProperties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${yeogirok.file.cleanup.safety-scan-interval}",
		initialDelayString = "${yeogirok.file.cleanup.safety-scan-interval}")
	public void deleteUnlinked() {
		String bucket = fileProperties.bucketFor(FilePurpose.TIP_PHOTO);
		try {
			Instant cutoff = clock.instant().minus(idempotencyProperties.retention());
			List<String> candidates = fileStorage.listKeysModifiedBefore(bucket, TIP_PHOTO_PREFIX, cutoff);
			if (candidates.isEmpty()) {
				return;
			}
			Set<String> keep = new HashSet<>(arTipImageRepository.findLinkedPaths(candidates));
			keep.addAll(publishStore.imagePaths());
			keep.addAll(editStore.imagePaths());
			for (String key : candidates) {
				if (!keep.contains(key)) {
					fileStorage.delete(bucket, key);
				}
			}
		} catch (RuntimeException e) {
			log.warn("팁 사진 정리에 실패해 다음 주기에 다시 시도합니다: {}", e.getClass().getSimpleName());
		}
	}
}
