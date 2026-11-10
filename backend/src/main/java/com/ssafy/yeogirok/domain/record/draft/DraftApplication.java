package com.ssafy.yeogirok.domain.record.draft;

import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.material.ReceiptMaterials;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * API-066 의 AI 초안 반영 검사와 반영 표시(명세 {@code specs/record-api-2.md} 4-9 2~6·8, 5절 17). 기록 행 잠금 안에서
 * {@link #check} 를 부르고, 커밋 뒤 {@link #markApplied} 를 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftApplication {

	private final RecordDraftStore draftStore;
	private final ReceiptMaterials materials;
	private final VisitImageRepository visitImageRepository;
	private final ActualVisitRepository actualVisitRepository;
	private final Clock clock;

	/**
	 * 판정 순서: 초안 없음·만료·다른 기록·SUCCEEDED 아님 RE011 → 반영됨 RE012 → draftVersion C004 → itemId 없음·중복 C003 →
	 * 반영 항목의 영수증 자료 version 이 바뀜(키가 남아 있을 때만) 또는 근거 사진이 지워짐 C004 → 이미 이 초안으로 만든 방문 RE012.
	 */
	public void check(Long recordId, VisitAddCommand command) {
		RecordDraft draft = draftStore.find(command.draftKey())
			.filter(found -> found.recordId().equals(recordId))
			.map(found -> found.asOf(clock.instant()))
			.filter(found -> found.status() == JobStatus.SUCCEEDED)
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_DRAFT_NOT_FOUND));
		if (draft.applied()) {
			throw new BusinessException(ExceptionType.RECORD_DRAFT_ALREADY_APPLIED);
		}
		if (!draft.draftVersion().equals(command.draftVersion())) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH, "draftVersion 이 현재 초안과 다릅니다.");
		}
		List<RecordDraft.Item> applying = applyingItems(draft, command);
		checkSources(recordId, draft, applying);
		// 기록 행 잠금으로 같은 기록의 066 이 한 줄로 서서 이 확인이 정확하다. DB 유일 제약이 마지막으로 막는다.
		if (actualVisitRepository.existsByRecordIdAndSourceDraftKey(recordId, draft.draftKey())) {
			throw new BusinessException(ExceptionType.RECORD_DRAFT_ALREADY_APPLIED);
		}
	}

	/** 커밋 뒤. Redis 갱신이 실패해도 방문은 DB 제약으로 중복되지 않아 로그만 남긴다. */
	public void markApplied(String draftKey) {
		try {
			draftStore.find(draftKey).ifPresent(draft -> draftStore.save(draft.markApplied()));
		} catch (RuntimeException e) {
			log.warn("기록 초안을 반영됨으로 표시하지 못했습니다. draftKey={}, cause={}", draftKey, e.getClass().getSimpleName());
		}
	}

	private static List<RecordDraft.Item> applyingItems(RecordDraft draft, VisitAddCommand command) {
		Map<Integer, RecordDraft.Item> byId = draft.items().stream()
			.collect(Collectors.toMap(RecordDraft.Item::itemId, Function.identity()));
		Set<Integer> seen = new HashSet<>();
		return command.items().stream()
			.map(VisitAddCommand.Item::itemId)
			.filter(itemId -> itemId != null)
			.map(itemId -> {
				if (!byId.containsKey(itemId) || !seen.add(itemId)) {
					throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
						"itemId: 초안에 없거나 두 번 보낸 항목입니다(" + itemId + ").");
				}
				return byId.get(itemId);
			})
			.toList();
	}

	private void checkSources(Long recordId, RecordDraft draft, List<RecordDraft.Item> applying) {
		Set<String> materialIds = applying.stream().flatMap(item -> item.sourceMaterialIds().stream())
			.collect(Collectors.toSet());
		for (RecordDraft.SnapshotMaterial snapshot : draft.sourceSnapshot().materials()) {
			if (materialIds.contains(snapshot.materialId())) {
				materials.find(snapshot.materialId())
					.filter(current -> current.version() != snapshot.version())
					.ifPresent(changed -> {
						throw new BusinessException(ExceptionType.VERSION_MISMATCH,
							"초안을 만든 뒤 영수증 자료가 바뀌었습니다(" + snapshot.materialId() + ").");
					});
			}
		}
		Set<Long> photoIds = applying.stream().flatMap(item -> item.sourcePhotoIds().stream())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		long remaining = visitImageRepository.findAllById(photoIds).stream()
			.filter(image -> image.getRecordId().equals(recordId))
			.count();
		if (remaining != photoIds.size()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH, "초안의 근거 사진이 지워졌습니다.");
		}
	}
}
