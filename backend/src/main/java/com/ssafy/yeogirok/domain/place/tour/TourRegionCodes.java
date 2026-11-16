package com.ssafy.yeogirok.domain.place.tour;

import java.util.List;
import java.util.Map;

/**
 * 지역 목록(API-078) code → TourAPI 조회 코드(lDongRegnCd·lDongSignguCd). 둘은 같은 법정동 체계라 대부분 앞 2자리·뒤 3자리로
 * 나누면 되지만, 일반구로 나뉜 뒤에도 TourAPI 에 옛 시 코드로 남은 장소가 많은 곳은 옛 코드도 함께 부른다.
 *
 * <p>화성시는 2026-02 에 일반구(만세·효행·병점·동탄구)로 나뉘었는데, TourAPI 에는 2026-11-16 기준 옛 코드 41590 으로 253곳,
 * 일반구로 130곳이 있다. 일반구로만 부르면 화성 장소 2/3 가 빠진다. TourAPI 가 옮기기를 마치면 이 표에서 뺀다.
 */
public final class TourRegionCodes {

	/** 일반구 code → TourAPI 에 남은 옛 시 시군구 코드(3자리). */
	private static final Map<String, String> LEGACY_CITY = Map.of(
		"41591", "590", "41593", "590", "41595", "590", "41597", "590");

	private TourRegionCodes() {
	}

	/** 시도 코드 2자리. regionCode 가 없으면 null(전국). */
	public static String sido(String regionCode) {
		return regionCode == null ? null : regionCode.substring(0, 2);
	}

	/**
	 * 부를 시군구 코드(3자리). 첫 값이 고른 시·군·구이고, 옛 시 코드가 남은 곳이면 그 코드가 뒤에 붙는다.
	 * regionCode 가 없거나 시도 2자리면 시군구로 거르지 않으므로 빈 목록이다.
	 */
	public static List<String> sigungus(String regionCode) {
		if (regionCode == null || regionCode.length() == 2) {
			return List.of();
		}
		String sigungu = regionCode.substring(2);
		String legacy = LEGACY_CITY.get(regionCode);
		return legacy == null ? List.of(sigungu) : List.of(sigungu, legacy);
	}
}
