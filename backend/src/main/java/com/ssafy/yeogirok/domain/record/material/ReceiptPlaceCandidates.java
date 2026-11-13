package com.ssafy.yeogirok.domain.record.material;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.RecordProperties;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch;
import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch.Place;

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
		List<RecordPlaceView> candidates = sameBranch(result, found.places()).stream()
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

	/**
	 * 영수증 주소 모양(건물번호 있음·도로명까지·시·군·구까지)에 따라 같은 지점으로 볼 결과만 남긴다(명세 5-3). 비교는 단어 단위다.
	 * 규격 밖 모양이면 빈 목록.
	 */
	private static List<Place> sameBranch(OcrResult result, List<Place> places) {
		List<String> words = words(result.address());
		String last = words.getLast();
		if (result.addressComplete() && words.size() >= 3 && isRoad(words.get(words.size() - 2))) {
			String road = words.get(words.size() - 2);
			// 건물번호는 잘못 읽을 수 있어(123 → 128) 같은 번호를 앞에 두되 도로명만 맞는 결과도 남긴다.
			return places.stream()
				.filter(place -> hasWord(place, road))
				.sorted(Comparator.comparing(place -> !nextWordIs(place, road, last)))
				.toList();
		}
		if (isRoad(last)) {
			return places.stream().filter(place -> hasWord(place, last)).toList();
		}
		if (!result.addressComplete() && isDistrict(last)) {
			// 같은 구에 지점이 여럿이면 어느 지점인지 알 수 없다.
			List<Place> inDistrict = places.stream().filter(place -> hasWord(place, last)).toList();
			return inDistrict.size() == 1 ? inDistrict : List.of();
		}
		return List.of();
	}

	/** 도로명은 대로·로·길로 끝난다(도로명주소법). */
	private static boolean isRoad(String word) {
		return word.endsWith("로") || word.endsWith("길");
	}

	private static boolean isDistrict(String word) {
		return word.endsWith("시") || word.endsWith("군") || word.endsWith("구");
	}

	private static boolean hasWord(Place place, String word) {
		return words(place.roadAddress()).contains(word) || words(place.address()).contains(word);
	}

	/** 결과 주소에서 road 바로 다음 단어가 building 인지. "123" 이 "1234"·"12길 3" 에 걸리지 않게 한다. */
	private static boolean nextWordIs(Place place, String road, String building) {
		return nextWordIs(words(place.roadAddress()), road, building) || nextWordIs(words(place.address()), road, building);
	}

	private static boolean nextWordIs(List<String> words, String road, String building) {
		int index = words.indexOf(road);
		return index >= 0 && index + 1 < words.size() && words.get(index + 1).equals(building);
	}

	private static List<String> words(String address) {
		return address == null || address.isBlank() ? List.of() : List.of(address.strip().split("\\s+"));
	}
}
