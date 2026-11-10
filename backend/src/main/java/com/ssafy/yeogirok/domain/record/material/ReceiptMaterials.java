package com.ssafy.yeogirok.domain.record.material;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.lock.RedisLock;

import lombok.RequiredArgsConstructor;

/**
 * 영수증 자료 읽기·쓰기(명세 {@code specs/record-api-2.md} 1-2·1-3).
 * <ul>
 *   <li>쓰기는 {@code material-lock:{materialId}} 잠금 안에서 읽기 → 검사 → 쓰기를 한다. 잠금을 못 잡으면 몇 번 다시 시도하고
 *   그래도 안 되면 C018(명세 5절 20).</li>
 *   <li>읽을 때 진행 중인 자료의 작업이 없거나(만료) 기한을 넘겨 실패로 보이면 자료도 실패로 보여 준다. 저장값은 다음 쓰기 때
 *   고친다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReceiptMaterials {

	private static final String LOCK_PREFIX = "material-lock:";
	/** 잠금 수명. 잠금 안에서는 Redis 읽기·쓰기만 한다(OCR·S3 호출은 잠금 밖). */
	private static final Duration LOCK_TTL = Duration.ofSeconds(5);
	private static final int LOCK_ATTEMPTS = 5;
	private static final long LOCK_RETRY_MILLIS = 100;

	private final ReceiptMaterialStore materialStore;
	private final JobStore jobStore;
	private final RedisLock redisLock;
	private final Clock clock;

	public Optional<ReceiptMaterial> find(String materialId) {
		return materialStore.find(materialId);
	}

	/**
	 * 잠금 안에서 자료를 바꿔 저장한다. {@code change} 가 BusinessException 을 던지면 저장하지 않는다.
	 * @return 바꾼 자료. 자료가 없으면 empty
	 */
	public Optional<ReceiptMaterial> update(String materialId, UnaryOperator<ReceiptMaterial> change) {
		return update(materialId, material -> true, change);
	}

	/** {@code condition} 을 만족할 때만 바꾼다(OCR 작업이 최신 jobId 일 때만 저장, 명세 1-2). 아니면 empty. */
	public Optional<ReceiptMaterial> update(String materialId, Predicate<ReceiptMaterial> condition,
		UnaryOperator<ReceiptMaterial> change) {
		String lockKey = LOCK_PREFIX + materialId;
		String token = acquire(lockKey);
		try {
			Optional<ReceiptMaterial> changed = materialStore.find(materialId).filter(condition).map(change);
			changed.ifPresent(material -> materialStore.replace(materialId, material));
			return changed;
		} finally {
			redisLock.release(lockKey, token);
		}
	}

	/** 지금 보여 줄 상태. 진행 중인데 작업이 끝내 실패했거나 사라졌으면 실패로 본다(1-3 끊긴 작업 판정). */
	public ReceiptMaterial current(ReceiptMaterial material) {
		if (!material.isInProgress()) {
			return material;
		}
		Optional<Job> job = jobStore.find(material.jobId()).map(found -> found.asOf(clock.instant()));
		if (job.isEmpty()) {
			return material.interrupted(toError(Job.TIMED_OUT));
		}
		if (job.get().status() == JobStatus.FAILED) {
			return material.interrupted(toError(job.get().error() == null ? Job.TIMED_OUT : job.get().error()));
		}
		return material;
	}

	/** 다른 기록·연결 전 자료는 없는 자료와 같게 RE008, 그다음 업로더가 아니면 M005(API-091·092 재전송). */
	public static void checkOwnLinked(ReceiptMaterial material, Long recordId, Long userId) {
		if (!material.isLinked() || !recordId.equals(material.recordId())) {
			throw new BusinessException(ExceptionType.MATERIAL_NOT_FOUND);
		}
		if (!userId.equals(material.uploadedBy())) {
			throw new BusinessException(ExceptionType.FILE_ACCESS_DENIED);
		}
	}

	/**
	 * API-091·092 공통 판정: RE008 → M005 → OCR 처리 중 RE022(끊긴 작업은 처리 중이 아니다) → version 불일치 C004.
	 * 자료 잠금 안에서 부른다.
	 */
	public void checkChangeable(ReceiptMaterial material, Long recordId, Long userId, int expectedVersion) {
		checkOwnLinked(material, recordId, userId);
		if (current(material).isInProgress()) {
			throw new BusinessException(ExceptionType.RECEIPT_NOT_READY);
		}
		if (material.version() != expectedVersion) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
	}

	private static ReceiptMaterial.Error toError(JobError error) {
		return new ReceiptMaterial.Error(error.code(), error.message());
	}

	private String acquire(String lockKey) {
		for (int attempt = 1; attempt <= LOCK_ATTEMPTS; attempt++) {
			Optional<String> token = redisLock.tryAcquire(lockKey, LOCK_TTL);
			if (token.isPresent()) {
				return token.get();
			}
			if (attempt < LOCK_ATTEMPTS) {
				pause();
			}
		}
		throw new BusinessException(ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS,
			"같은 자료를 처리하고 있습니다. 잠시 후 다시 시도해 주세요.");
	}

	private static void pause() {
		try {
			Thread.sleep(LOCK_RETRY_MILLIS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("자료 잠금을 기다리다 중단됐습니다.", e);
		}
	}
}
