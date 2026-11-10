package com.ssafy.yeogirok.domain.record.material;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.repository.RecordPlaceRepository;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-091 영수증 OCR 결과 보정·확인(명세 {@code specs/record-api-2.md} 4-5). 자료(Redis)만 바꾼다. 실제 방문·places 행·기록
 * version 은 그대로이고, 직접 입력 장소의 places 행은 066 반영 때 만든다(1-4).
 *
 * <p>원본 정리를 앞당기지 않는다: 확정한 뒤에도 092 로 다시 OCR 할 수 있어 원본이 24시간 기한까지 필요하다.
 */
@Service
@RequiredArgsConstructor
public class MaterialReviewService {

	private final RecordFinder recordFinder;
	private final ReceiptMaterials materials;
	private final RecordPlaceRepository recordPlaceRepository;

	/** 판정 순서: RE001/RE003 → RE008 → M005 → RE022(OCR 처리 중) → C004 → 투어API 장소 없음 C003. */
	public MaterialReviewResponse review(Long userId, Long recordId, String materialId, MaterialReviewCommand command) {
		recordFinder.checkWritable(recordId, userId);
		ReceiptMaterial saved = materials.update(materialId, material -> {
			materials.checkChangeable(material, recordId, userId, command.version());
			return material.reviewed(new ReceiptMaterial.Values(command.merchantName(), command.paidAt().toInstant(),
				command.paidAt().toLocalDate(), command.paidAt().toLocalTime(), place(command.place())));
		}).orElseThrow(() -> new BusinessException(ExceptionType.MATERIAL_NOT_FOUND));
		return MaterialReviewResponse.of(materialId, saved);
	}

	/** 같은 멱등키 재전송. 권한을 다시 확인하고 지금 자료 값으로 답한다(명세 1-5). */
	public MaterialReviewResponse getReviewed(Long userId, Long recordId, String materialId) {
		recordFinder.checkWritable(recordId, userId);
		ReceiptMaterial material = materials.find(materialId)
			.orElseThrow(() -> new BusinessException(ExceptionType.MATERIAL_NOT_FOUND));
		ReceiptMaterials.checkOwnLinked(material, recordId, userId);
		return MaterialReviewResponse.of(materialId, materials.current(material));
	}

	/** 투어API 장소는 places 에 이미 있는 행만 받는다(1차 5절 6, 투어API 클라이언트 전까지). */
	private RecordPlaceView place(VisitPlaceInput input) {
		return switch (input) {
			case VisitPlaceInput.TourPlace tour -> RecordPlaceView.from(recordPlaceRepository
				.findByProviderAndProviderPlaceId(PlaceProvider.TOUR_API, tour.providerPlaceId())
				.orElseThrow(() -> new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
					"placeRef: 아직 저장되지 않은 투어API 장소입니다.")));
			case VisitPlaceInput.CustomPlace custom -> new RecordPlaceView(PlaceProvider.MANUAL, null, custom.name(),
				custom.latitude(), custom.longitude(), custom.address(), null, null, null);
		};
	}
}
