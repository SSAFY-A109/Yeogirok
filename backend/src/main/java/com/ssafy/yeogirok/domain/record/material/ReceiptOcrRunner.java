package com.ssafy.yeogirok.domain.record.material;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import lombok.extern.slf4j.Slf4j;

/**
 * 영수증 OCR 작업(RECEIPT_OCR, 명세 {@code specs/record-api-2.md} 4-1). 자료를 Redis 에 저장한 뒤 트랜잭션 밖에서 부른다.
 * 자료 쓰기는 늘 "그 자료의 jobId 가 이 작업일 때만" 해서 늦게 끝난 지난 작업이 새 작업 결과를 덮지 않는다.
 */
@Slf4j
@Component
public class ReceiptOcrRunner {

	public static final String TYPE = "RECEIPT_OCR";
	/** 다시 시도하면 될 수 있는 업무 오류(PR #84 리뷰). 작업에는 C014·retryable 로 남긴다. */
	private static final Set<ExceptionType> TRANSIENT = Set.of(ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS,
		ExceptionType.FILE_STORAGE_UNAVAILABLE);

	private final JobRunner jobRunner;
	private final ReceiptMaterials materials;
	private final FileStorage fileStorage;
	private final FileProperties fileProperties;
	private final ReceiptOcrClient ocrClient;
	private final OcrProperties ocrProperties;
	private final Clock clock;
	/** OCR 컨테이너 메모리 때문에 한 번에 한 건만 부른다(5절 2). 작업 스레드가 2개라 기다리는 건 최대 한 건. */
	private final Semaphore ocrSlot = new Semaphore(1, true);

	public ReceiptOcrRunner(JobRunner jobRunner, ReceiptMaterials materials, FileStorage fileStorage,
		FileProperties fileProperties, ReceiptOcrClient ocrClient, OcrProperties ocrProperties, Clock clock) {
		this.jobRunner = jobRunner;
		this.materials = materials;
		this.fileStorage = fileStorage;
		this.fileProperties = fileProperties;
		this.ocrClient = ocrClient;
		this.ocrProperties = ocrProperties;
		this.clock = clock;
	}

	public String newJobId() {
		return jobRunner.newId();
	}

	/** 자료의 jobId 가 {@code jobId} 로 저장된 뒤 부른다. 실행기에 못 맡기면 자료를 바로 실패로 바꾼다. */
	public void submit(String materialId, String jobId, ReceiptMaterial material) {
		Job job = jobRunner.submit(jobId, TYPE, material.uploadedBy(), material.recordId().toString(),
			() -> recognize(materialId, jobId), () -> fail(materialId, jobId, Job.TIMED_OUT.code(),
				Job.TIMED_OUT.message()));
		if (job.status() == JobStatus.FAILED) {
			fail(materialId, jobId, job.error().code(), job.error().message());
		}
	}

	Map<String, Object> recognize(String materialId, String jobId) {
		try {
			ReceiptMaterial started = materials.update(materialId, current -> jobId.equals(current.jobId()),
				ReceiptMaterial::extracting).orElse(null);
			if (started == null) {
				return staleResult(materialId);
			}
			if (!started.isOriginalAvailable(clock.instant())) {
				throw new BusinessException(ExceptionType.FILE_NOT_FOUND, "영수증 원본이 삭제됐거나 보관 기한이 지났습니다.");
			}
			OcrResult result = callOcr(readOriginal(started));
			return materials.update(materialId, current -> jobId.equals(current.jobId()),
					current -> current.ocrSucceeded(extracted(result)))
				.map(saved -> result(materialId, saved.version()))
				.orElseGet(() -> staleResult(materialId));
		} catch (BusinessException e) {
			if (TRANSIENT.contains(e.getExceptionType())) {
				// 자료 잠금 대기 초과·저장소 일시 장애는 다시 시도할 수 있게 일반 오류로 넘긴다(C014, retryable).
				fail(materialId, jobId, Job.TIMED_OUT.code(), ExceptionType.SERVER_ERROR.getMessage());
				throw new IllegalStateException("영수증 OCR 작업이 일시 오류로 끝났습니다.", e);
			}
			fail(materialId, jobId, e.getExceptionType().getCode(), e.getMessage());
			throw e;
		} catch (RuntimeException e) {
			fail(materialId, jobId, Job.TIMED_OUT.code(), ExceptionType.SERVER_ERROR.getMessage());
			throw e;
		}
	}

	private byte[] readOriginal(ReceiptMaterial material) {
		return fileStorage.readAll(fileProperties.receiptBucket(), material.objectKey(),
			fileProperties.maxBytesOf(FilePurpose.RECEIPT));
	}

	private OcrResult callOcr(byte[] image) {
		boolean acquired;
		try {
			acquired = ocrSlot.tryAcquire(ocrProperties.slotWait().toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("OCR 차례를 기다리다 중단됐습니다.", e);
		}
		if (!acquired) {
			throw new IllegalStateException("OCR 차례를 기다리는 시간이 지났습니다.");
		}
		try {
			return ocrClient.recognize(image);
		} finally {
			ocrSlot.release();
		}
	}

	/** 날짜·시각이 둘 다 있을 때만 +09:00 으로 조합한다(5절 6). 장소 후보는 TourAPI 전까지 없다(5절 7). */
	static ReceiptMaterial.Extracted extracted(OcrResult result) {
		var paidAt = result.paidDate() == null || result.paidTime() == null ? null
			: LocalDateTime.of(result.paidDate(), result.paidTime()).atZone(ApiDateTime.ZONE).toInstant();
		return new ReceiptMaterial.Extracted(result.merchantName(), result.paidDate(), result.paidTime(), paidAt,
			List.of(), null);
	}

	private void fail(String materialId, String jobId, String code, String message) {
		try {
			materials.update(materialId, current -> jobId.equals(current.jobId()) && current.isInProgress(),
				current -> current.ocrFailed(new ReceiptMaterial.Error(code, message)));
		} catch (RuntimeException e) {
			// 저장하지 못해도 읽을 때 끊긴 작업 판정으로 실패로 보인다(1-3).
			log.warn("영수증 자료를 실패로 바꾸지 못했습니다. materialId={}, jobId={}, cause={}", materialId, jobId,
				e.getClass().getSimpleName());
		}
	}

	private Map<String, Object> staleResult(String materialId) {
		log.info("지난 OCR 작업이라 자료를 바꾸지 않습니다. materialId={}", materialId);
		return result(materialId, materials.find(materialId).map(ReceiptMaterial::version).orElse(null));
	}

	private static Map<String, Object> result(String materialId, Integer version) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("type", TYPE);
		result.put("materialId", materialId);
		result.put("materialVersion", version);
		return result;
	}
}
