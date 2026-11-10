package com.ssafy.yeogirok.domain.record.material;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialEntry;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.record.dto.MaterialListResponse;
import com.ssafy.yeogirok.domain.record.dto.MaterialSummary;
import com.ssafy.yeogirok.domain.record.dto.MaterialView;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;

import lombok.RequiredArgsConstructor;

/** 요청자 본인이 기록에 연결한 영수증 자료 조회(API-063, 059 materials). 다른 참여자의 자료는 보여 주지 않는다. */
@Service
@RequiredArgsConstructor
public class MaterialQueryService {

	private static final Comparator<ReceiptMaterialEntry> UPLOAD_ORDER = Comparator
		.comparing((ReceiptMaterialEntry entry) -> entry.material().uploadAcceptedAt())
		.thenComparing(ReceiptMaterialEntry::materialId);

	private final RecordFinder recordFinder;
	private final ReceiptMaterialStore materialStore;
	private final ReceiptMaterials materials;
	private final Clock clock;

	/** API-063. 기록 없음·방 삭제 RE001, 현재 참여자 아님 RE002. */
	public MaterialListResponse list(Long userId, Long recordId) {
		recordFinder.getReadable(recordId, userId);
		Instant now = clock.instant();
		return new MaterialListResponse(mine(recordId, userId).stream()
			.map(entry -> MaterialView.of(entry.materialId(), entry.material(), now))
			.toList());
	}

	/** API-059 materials. 권한은 부르는 쪽이 확인한다. */
	public List<MaterialSummary> summaries(Long recordId, Long userId) {
		return mine(recordId, userId).stream()
			.map(entry -> MaterialSummary.of(entry.materialId(), entry.material()))
			.toList();
	}

	/** 여러 기록의 본인 자료(API-058·059 progress). Redis 를 기록마다 따로 읽지 않는다. */
	public Map<Long, List<ReceiptMaterial>> mineByRecord(List<Long> recordIds, Long userId) {
		Map<Long, List<ReceiptMaterial>> byRecord = new HashMap<>();
		materialStore.indexedAll(recordIds, userId).forEach((recordId, entries) -> byRecord.put(recordId,
			own(recordId, userId, entries).stream().map(ReceiptMaterialEntry::material).toList()));
		return byRecord;
	}

	/** 연결한 본인 자료를 업로드 접수 순으로(명세 5절 12). 끊긴 작업은 실패로 바꿔 보여 준다. */
	List<ReceiptMaterialEntry> mine(Long recordId, Long userId) {
		return own(recordId, userId, materialStore.indexed(recordId, userId));
	}

	private List<ReceiptMaterialEntry> own(Long recordId, Long userId, List<ReceiptMaterialEntry> entries) {
		return entries.stream()
			.filter(entry -> entry.material().isLinked() && recordId.equals(entry.material().recordId())
				&& userId.equals(entry.material().uploadedBy()))
			.map(entry -> new ReceiptMaterialEntry(entry.materialId(), materials.current(entry.material())))
			.sorted(UPLOAD_ORDER)
			.toList();
	}
}
