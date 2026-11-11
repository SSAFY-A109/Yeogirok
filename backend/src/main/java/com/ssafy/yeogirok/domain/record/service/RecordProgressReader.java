package com.ssafy.yeogirok.domain.record.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialEntry;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftStore;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.material.MaterialQueryService;
import com.ssafy.yeogirok.domain.record.repository.RecordPhotoCount;
import com.ssafy.yeogirok.domain.record.repository.VisitImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 기록들의 progress(API-058·059, 명세 {@code specs/record-api-2.md} 4-10). 기록마다 따로 읽지 않는다: 초안·포인터는 MGET,
 * 본인 자료는 파이프라인 + MGET, 사진 수는 쿼리 한 번. 완성된 기록은 더 읽지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RecordProgressReader {

	private final RecordDraftStore draftStore;
	private final MaterialQueryService materialQueryService;
	private final VisitImageRepository visitImageRepository;
	private final Clock clock;

	/** @param visitCounts 기록 ID → 실제 방문 수(없으면 0) */
	public Map<Long, RecordProgress> read(Long userId, List<TravelRecord> records, Map<Long, Integer> visitCounts) {
		return read(records, visitCounts, materialQueryService.mineByRecord(openIds(records), userId));
	}

	/** 본인 자료를 이미 읽었을 때(API-059 는 materials 응답에도 같은 자료를 쓴다). */
	public Map<Long, RecordProgress> read(List<TravelRecord> records, Map<Long, Integer> visitCounts,
		Map<Long, List<ReceiptMaterialEntry>> mine) {
		List<Long> open = openIds(records);
		Instant now = clock.instant();
		Map<Long, RecordDraft> drafts = draftStore.findLatest(open);
		Map<Long, Long> photos = open.isEmpty() ? Map.of() : visitImageRepository.countByRecordIds(open).stream()
			.collect(Collectors.toMap(RecordPhotoCount::recordId, RecordPhotoCount::count));
		Map<Long, RecordProgress> progress = new HashMap<>();
		for (TravelRecord record : records) {
			RecordDraft draft = drafts.get(record.getId());
			progress.put(record.getId(), RecordProgress.of(record.getStatus(),
				visitCounts.getOrDefault(record.getId(), 0), draft == null ? null : draft.asOf(now),
				mine.getOrDefault(record.getId(), List.of()).stream().map(ReceiptMaterialEntry::material).toList(),
				photos.getOrDefault(record.getId(), 0L)));
		}
		return progress;
	}

	private static List<Long> openIds(List<TravelRecord> records) {
		return records.stream().filter(record -> record.getStatus() != RecordStatus.COMPLETED)
			.map(TravelRecord::getId).toList();
	}
}
