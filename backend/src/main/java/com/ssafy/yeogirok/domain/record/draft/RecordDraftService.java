package com.ssafy.yeogirok.domain.record.draft;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.record.dto.DraftJobResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.material.ReceiptMaterials;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-064 통합 기록 초안 생성 접수(명세 {@code specs/record-api-2.md} 4-7). 트랜잭션 밖에서 돈다. DB 에 쓰지 않아 기록 status·version
 * 은 바뀌지 않는다. 입력은 접수 때 고정하고(영수증은 values 만) 만들기는 작업 스레드의 {@link RecordDraftGenerator} 가 한다.
 */
@Service
@RequiredArgsConstructor
public class RecordDraftService {

	private final RecordFinder recordFinder;
	private final VisitImageRepository visitImageRepository;
	private final ReceiptMaterials materials;
	private final RecordDraftStore draftStore;
	private final RecordDraftGenerator generator;
	private final JobRunner jobRunner;
	private final JobProperties jobProperties;
	private final Clock clock;

	/** 판정 순서: RE001/RE003 → baseVersion C004 → 사진 RE008 → 영수증마다 RE008 → M005 → C004 → RE022. */
	public DraftJobResponse create(Long userId, Long recordId, DraftCreateCommand command) {
		TravelRecord record = recordFinder.checkWritable(recordId, userId);
		if (record.getVersion() != command.baseVersion()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		long ownImages = visitImageRepository.findAllById(command.imageIds()).stream()
			.filter(image -> image.getRecordId().equals(recordId))
			.count();
		if (ownImages != command.imageIds().size()) {
			throw new BusinessException(ExceptionType.MATERIAL_NOT_FOUND, "이 기록의 사진이 아닌 imageId 가 있습니다.");
		}
		List<RecordDraft.SnapshotMaterial> snapshot = new ArrayList<>();
		for (DraftCreateCommand.MaterialRef ref : command.materials()) {
			snapshot.add(usable(ref, recordId, userId));
		}

		Instant now = clock.instant();
		String jobId = jobRunner.newId();
		RecordDraft draft = RecordDraft.queued(UUID.randomUUID().toString(), jobId, recordId, userId,
			command.baseVersion(), new RecordDraft.SourceSnapshot(command.imageIds(), snapshot, List.of()), now,
			now.plus(jobProperties.maxLifetime()));
		draftStore.save(draft);
		draftStore.savePointer(draft);
		Job job = jobRunner.submit(jobId, RecordGenerationJobPolicy.TYPE, userId, recordId.toString(),
			() -> generator.generate(draft.draftKey()), () -> generator.expire(draft.draftKey()));
		if (job.status() == JobStatus.FAILED) {
			generator.fail(draft.draftKey(), job.error());
		}
		return DraftJobResponse.queued(draft.draftKey(), jobId);
	}

	/** 같은 멱등키 재전송. 권한을 다시 확인하고 처음과 같은 모양으로 답한다(명세 1-5). */
	public DraftJobResponse created(Long userId, Long recordId, String draftKey) {
		recordFinder.checkWritable(recordId, userId);
		RecordDraft draft = draftStore.find(draftKey)
			.filter(found -> found.recordId().equals(recordId))
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_DRAFT_NOT_FOUND));
		return DraftJobResponse.queued(draft.draftKey(), draft.jobId());
	}

	private RecordDraft.SnapshotMaterial usable(DraftCreateCommand.MaterialRef ref, Long recordId, Long userId) {
		ReceiptMaterial material = materials.find(ref.materialId())
			.orElseThrow(() -> new BusinessException(ExceptionType.MATERIAL_NOT_FOUND));
		ReceiptMaterials.checkOwnLinked(material, recordId, userId);
		if (material.version() != ref.version()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		if (!materials.current(material).canUseForDraft()) {
			throw new BusinessException(ExceptionType.RECEIPT_NOT_READY);
		}
		return new RecordDraft.SnapshotMaterial(ref.materialId(), ref.version(), material.values());
	}
}
