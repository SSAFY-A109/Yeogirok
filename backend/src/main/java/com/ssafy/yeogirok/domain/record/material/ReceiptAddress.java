package com.ssafy.yeogirok.domain.record.material;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch.Place;

/**
 * OCR v3 영수증 주소로 카카오 결과 중 같은 지점을 고른다(명세 {@code specs/draft-merge.md} 5-3). 비교는 모두 단어 단위다.
 *
 * @param province  시·도(줄인 이름으로 맞춘 값). 이름 표에 없으면 null(시·도는 비교하지 않는다)
 * @param districts 시·도 다음의 시·군·구 단어들(예: 수원시·장안구)
 * @param road      도로명. 시·군·구까지만 읽은 주소면 null
 * @param building  건물번호. addressComplete 일 때만 있다
 */
record ReceiptAddress(String province, List<String> districts, String road, String building) {

	/** 시·도 이름(줄인·정식·바뀌기 전 이름) → 줄인 이름. 영수증은 "부산시", 카카오는 "부산"처럼 표기가 달라서 맞춘다. */
	private static final Map<String, String> PROVINCES = Stream.of(
			List.of("서울", "서울시", "서울특별시"), List.of("부산", "부산시", "부산광역시"),
			List.of("대구", "대구시", "대구광역시"), List.of("인천", "인천시", "인천광역시"),
			List.of("광주", "광주시", "광주광역시"), List.of("대전", "대전시", "대전광역시"),
			List.of("울산", "울산시", "울산광역시"), List.of("세종", "세종시", "세종특별자치시"),
			List.of("경기", "경기도"), List.of("강원", "강원도", "강원특별자치도"), List.of("충북", "충청북도"),
			List.of("충남", "충청남도"), List.of("전북", "전라북도", "전북특별자치도"), List.of("전남", "전라남도"),
			List.of("경북", "경상북도"), List.of("경남", "경상남도"), List.of("제주", "제주도", "제주특별자치도"))
		.flatMap(names -> names.stream().map(name -> Map.entry(name, names.getFirst())))
		.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

	/** @return 규격 밖 모양(도로명도 시·군·구로 끝나지도 않음)이면 empty */
	static Optional<ReceiptAddress> parse(String address, boolean addressComplete) {
		List<String> words = words(address);
		if (words.size() < 2) {
			return Optional.empty();
		}
		String last = words.getLast();
		String beforeLast = words.get(words.size() - 2);
		String road = null;
		String building = null;
		if (isRoad(last)) {
			road = last;
		} else if (words.size() >= 3 && isRoad(beforeLast)) {
			// addressComplete false 인데 번호가 붙었으면 번호를 다 못 읽은 것일 수 있어 도로명까지만 쓴다.
			road = beforeLast;
			building = addressComplete ? last : null;
		} else if (addressComplete || !isDistrict(last)) {
			return Optional.empty();
		}
		List<String> districts = words.subList(1, words.size()).stream().filter(ReceiptAddress::isDistrict).toList();
		return Optional.of(new ReceiptAddress(PROVINCES.get(words.getFirst()), districts, road, building));
	}

	/**
	 * 같은 시·도·시·군·구 안에서, 도로명이 있으면 도로명이 맞는 결과(건물번호까지 같은 결과를 앞에, 번호 오인식 123 → 128 대비 도로명만
	 * 맞는 결과도 남김), 시·군·구까지만이면 남은 결과가 정확히 1개일 때만 그 결과(같은 구에 지점이 여럿이면 어느 지점인지 알 수 없다).
	 */
	List<Place> sameBranch(List<Place> places) {
		List<Place> inRegion = places.stream().filter(this::sameRegion).toList();
		if (road == null) {
			return inRegion.size() == 1 ? inRegion : List.of();
		}
		return inRegion.stream()
			.filter(place -> hasWord(place, road))
			.sorted(Comparator.comparing(place -> building == null || !nextWordIs(place, road, building)))
			.toList();
	}

	/** 시·도는 양쪽 다 이름 표에 있을 때만 비교한다(예: 표에 없는 새 통합 시·도). 시·군·구는 모두 결과 주소에 있어야 한다. */
	private boolean sameRegion(Place place) {
		return districts.stream().allMatch(district -> hasWord(place, district))
			&& (province == null || Stream.of(place.roadAddress(), place.address())
				.map(ReceiptAddress::words)
				.filter(words -> !words.isEmpty() && PROVINCES.containsKey(words.getFirst()))
				.allMatch(words -> province.equals(PROVINCES.get(words.getFirst()))));
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
