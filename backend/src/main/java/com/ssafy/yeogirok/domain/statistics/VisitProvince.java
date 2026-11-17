package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * API-046 전국 보기(zoom 이 province-below-zoom 보다 작을 때)의 도 9곳. 특별시·광역시·세종은 둘러싼 도에 합친다(2026-11-17 결정).
 * 발자취는 법정동 코드 앞 2자리로 나눈다. 바뀌기 전 코드(강원 42, 전북 45, 광주 29, 전남 46)도 받는다.
 * 코드가 없거나 모르는 코드면 위치가 가장 가까운 도의 마커 위치로 고른다({@link #nearest}). 탈퇴자 보존분도 코드가 없어 같다.
 * 마커 위치는 도마다 정해 둔 땅 위 한 점이다. 발자취 평균을 쓰지 않아 한두 명의 위치를 역산할 수 없다.
 * 순서는 도 순서(경기·강원·충북·충남·전북·전남광주·경북·경남·제주)이고 응답 cells 도 이 순서다.
 */
enum VisitProvince {

	GYEONGGI("41", "서울·인천·경기", "37.5000000", "127.2000000", "41", "11", "28"),
	GANGWON("51", "강원", "37.7500000", "128.3000000", "51", "42"),
	CHUNGBUK("43", "충북", "36.7500000", "127.7500000", "43"),
	CHUNGNAM("44", "대전·세종·충남", "36.5000000", "126.8500000", "44", "30", "36"),
	JEONBUK("52", "전북", "35.7200000", "127.1500000", "52", "45"),
	JEONNAM_GWANGJU("12", "전남광주", "34.9000000", "126.9500000", "12", "29", "46"),
	GYEONGBUK("47", "대구·경북", "36.3500000", "128.7500000", "47", "27"),
	GYEONGNAM("48", "부산·울산·경남", "35.3500000", "128.3500000", "48", "26", "31"),
	JEJU("50", "제주", "33.3800000", "126.5500000", "50");

	/** cellId 앞에 붙여 격자 칸("배수:위도칸:경도칸")과 구분한다. */
	static final String CELL_ID_PREFIX = "province:";

	/** 경도 1도는 위도 36도(한국 가운데쯤)에서 위도 1도의 cos(36°)배 길이다. 가까운 도를 고를 때만 쓰는 근사다. */
	private static final double LONGITUDE_SCALE = Math.cos(Math.toRadians(36));

	private static final Map<String, VisitProvince> BY_LEGAL_AREA_PREFIX = Arrays.stream(values())
		.flatMap(province -> province.legalAreaPrefixes.stream().map(prefix -> Map.entry(prefix, province)))
		.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

	private final String code;
	private final String displayName;
	private final BigDecimal latitude;
	private final BigDecimal longitude;
	private final List<String> legalAreaPrefixes;

	VisitProvince(String code, String displayName, String latitude, String longitude, String... legalAreaPrefixes) {
		this.code = code;
		this.displayName = displayName;
		this.latitude = new BigDecimal(latitude);
		this.longitude = new BigDecimal(longitude);
		this.legalAreaPrefixes = Stream.of(legalAreaPrefixes).toList();
	}

	/** 법정동 코드 앞 2자리의 도. 없거나 모르는 코드면 빈 값. */
	static Optional<VisitProvince> ofLegalAreaPrefix(String prefix) {
		return Optional.ofNullable(prefix).map(BY_LEGAL_AREA_PREFIX::get);
	}

	/** 마커 위치가 가장 가까운 도. 경계 근처에서는 실제 도와 다를 수 있어 법정동 코드가 없을 때만 쓴다. */
	static VisitProvince nearest(BigDecimal latitude, BigDecimal longitude) {
		double lat = latitude.doubleValue();
		double lng = longitude.doubleValue();
		return Arrays.stream(values())
			.min(Comparator.comparingDouble(province -> province.distanceSquared(lat, lng)))
			.orElseThrow();
	}

	String cellId() {
		return CELL_ID_PREFIX + code;
	}

	String displayName() {
		return displayName;
	}

	BigDecimal latitude() {
		return latitude;
	}

	BigDecimal longitude() {
		return longitude;
	}

	private double distanceSquared(double lat, double lng) {
		double dLat = lat - latitude.doubleValue();
		double dLng = (lng - longitude.doubleValue()) * LONGITUDE_SCALE;
		return dLat * dLat + dLng * dLng;
	}
}
