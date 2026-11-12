package com.ssafy.yeogirok.domain.place.tour;

/**
 * 지역 기반 관광정보 조회(areaBasedList2) 조건. 지역은 법정동 코드, 분류는 신분류체계(lclsSystm)로 거른다.
 * 구 지역 코드(areaCode·sigunguCode)·구 분류(cat1~3)는 TourAPI 에서 삭제 예정이라 쓰지 않는다.
 *
 * @param lDongRegnCd   법정동 시도 코드 2자리
 * @param lDongSignguCd 법정동 시군구 코드 3자리. 시도 전체면 null
 * @param lclsSystm1    분류 1단계. 없으면 null
 * @param lclsSystm2    분류 2단계(1단계 필수). 없으면 null
 * @param numOfRows     한 번에 받을 개수
 */
public record TourAreaQuery(
	String lDongRegnCd,
	String lDongSignguCd,
	String lclsSystm1,
	String lclsSystm2,
	int numOfRows
) {
}
