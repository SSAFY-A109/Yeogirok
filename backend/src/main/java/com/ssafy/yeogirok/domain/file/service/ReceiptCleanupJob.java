package com.ssafy.yeogirok.domain.file.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.session.ReceiptCleanupQueue;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.record.RecordProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 영수증 원본 24시간 정리(노션 API-012·Redis 4절, specs/file-api.md 4-5). Redis 만료에만 맡기지 않는다.
 * <ol>
 *   <li>기한 목록: 기한 지난 원본을 S3 에서 지운 뒤에만 목록에서 뺀다. 실패하면 남겨 다음 주기에 다시 시도한다.</li>
 *   <li>안전망: Redis 가 유실돼도 영수증 버킷을 직접 훑어 보관 기간이 지난 객체를 지운다. 한 객체가 실패하면 기한 목록에 넣어
 *   다음 기한 정리가 다시 시도한다(원본을 지운 뒤 자료 표시만 실패하면 다음 스캔에 그 객체가 안 나오므로). S3 수명 주기는 마지막
 *   안전망이다.</li>
 * </ol>
 * 확정 원본(receipt/{uploadId})을 지우면 자료의 경로를 비우고 DELETED 로 표시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptCleanupJob {

	private static final String CONFIRMED_PREFIX = FilePurpose.RECEIPT.confirmedKey("");

	private final ReceiptCleanupQueue cleanupQueue;
	private final ReceiptMaterialStore materialStore;
	private final FileStorage fileStorage;
	private final FileProperties properties;
	private final RecordProperties recordProperties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${yeogirok.file.cleanup.due-interval}",
		initialDelayString = "${yeogirok.file.cleanup.due-interval}")
	public void deleteDue() {
		for (String member : cleanupQueue.due(clock.instant())) {
			int slash = member.indexOf('/');
			try {
				deleteOriginal(member.substring(0, slash), member.substring(slash + 1));
				cleanupQueue.remove(member);
			} catch (RuntimeException e) {
				log.warn("영수증 원본을 지우지 못해 다음 주기에 다시 시도합니다: {}", e.getClass().getSimpleName());
			}
		}
	}

	@Scheduled(fixedDelayString = "${yeogirok.file.cleanup.safety-scan-interval}",
		initialDelayString = "${yeogirok.file.cleanup.safety-scan-interval}")
	public void deleteExpiredInBucket() {
		Instant cutoff = clock.instant().minus(properties.receiptRetention());
		String bucket = properties.receiptBucket();
		List<String> keys;
		try {
			keys = fileStorage.listKeysModifiedBefore(bucket, cutoff);
		} catch (RuntimeException e) {
			log.warn("영수증 버킷 안전망 정리에 실패해 다음 주기에 다시 시도합니다: {}", e.getClass().getSimpleName());
			return;
		}
		for (String key : keys) {
			try {
				deleteOriginal(bucket, key);
			} catch (RuntimeException e) {
				requeue(bucket, key, e);
			}
		}
	}

	private void requeue(String bucket, String key, RuntimeException cause) {
		log.warn("영수증 원본 정리에 실패해 기한 목록에 넣어 다시 시도합니다: {}", cause.getClass().getSimpleName());
		try {
			cleanupQueue.register(bucket, key, clock.instant());
		} catch (RuntimeException e) {
			log.error("영수증 원본 정리 대상을 기한 목록에도 넣지 못했습니다. key={}, cause={}", key, e.getClass().getSimpleName());
		}
	}

	private void deleteOriginal(String bucket, String key) {
		fileStorage.delete(bucket, key);
		if (key.startsWith(CONFIRMED_PREFIX)) {
			materialStore.markOriginalDeleted(ReceiptMaterial.idOf(key.substring(CONFIRMED_PREFIX.length())),
				recordProperties.materialRetention());
		}
	}
}
