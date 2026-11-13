package com.ssafy.yeogirok.domain.record.material;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.RecordProperties;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OCR 이 읽은 영수증 주소·상호로 장소 후보를 찾는다(명세 {@code specs/draft-merge.md} 5절). 후보는 063
 * {@code extracted.placeCandidates} 로만 보여 주는 제안이고 자동으로 확정하지 않는다. 사용자가 091 에서 고르면 직접 입력(MANUAL)
 * 장소로 확정된다. 실패해도 OCR 결과는 그대로 저장되도록 예외를 밖으로 던지지 않는다. 주소·상호 원문은 로그에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptPlaceCandidates {

	private final PlaceKeywordSearch search;
	private final RecordProperties properties;

	/** @return 주소·상호 중 하나라도 없거나 찾지 못하면 빈 목록 */
	public List<RecordPlaceView> find(String materialId, OcrResult result) {
		if (result.address() == null || result.merchantName() == null) {
			return List.of();
		}
		PlaceKeywordSearch.Result found;
		try {
			found = search.search(result.address() + " " + result.merchantName());
		} catch (RuntimeException e) {
			log.warn("영수증 장소 후보 검색 실패. materialId={}, reason={}", materialId, e.getClass().getSimpleName());
			return List.of();
		}
		if (found.outcome() == PlaceKeywordSearch.Outcome.FAILED) {
			log.warn("영수증 장소 후보 검색 실패. materialId={}, reason={}", materialId, found.failure());
			return List.of();
		}
		if (found.outcome() == PlaceKeywordSearch.Outcome.NO_KEY) {
			log.info("영수증 장소 후보 검색 안 함(카카오 키 없음). materialId={}", materialId);
			return List.of();
		}
		// 영수증 주소의 마지막 단어(도로명, 지번만이면 시·군·구)가 결과 주소에 있어야 같은 지점으로 본다(체인 다른 지점 제외).
		String street = lastWord(result.address());
		List<RecordPlaceView> candidates = found.places().stream()
			.filter(place -> contains(place.roadAddress(), street) || contains(place.address(), street))
			.limit(properties.receiptPlaceCandidates())
			.map(place -> new RecordPlaceView(PlaceProvider.MANUAL, null, place.name(), place.latitude(),
				place.longitude(), place.roadAddress() != null ? place.roadAddress() : place.address(), null, null, null))
			.toList();
		if (candidates.isEmpty()) {
			log.info("영수증 장소 후보 결과 없음. materialId={}", materialId);
		} else {
			log.info("영수증 장소 후보를 찾았습니다. materialId={}, count={}", materialId, candidates.size());
		}
		return candidates;
	}

	private static String lastWord(String address) {
		String[] words = address.strip().split("\\s+");
		return words[words.length - 1];
	}

	private static boolean contains(String address, String word) {
		return address != null && address.contains(word);
	}
}
