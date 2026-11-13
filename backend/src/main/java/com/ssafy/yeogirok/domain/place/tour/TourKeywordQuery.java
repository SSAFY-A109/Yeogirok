package com.ssafy.yeogirok.domain.place.tour;

/**
 * 키워드 검색(searchKeyword2) 조건. 지역은 법정동 코드로 거른다({@link TourAreaQuery} 와 같은 단위).
 *
 * @param keyword       검색어. 앞뒤 공백을 뺀 값
 * @param lDongRegnCd   법정동 시도 코드 2자리. 전국이면 null
 * @param lDongSignguCd 법정동 시군구 코드 3자리. 시도 전체·전국이면 null
 * @param pageNo        1부터 시작하는 쪽 번호
 * @param numOfRows     한 쪽 개수
 */
public record TourKeywordQuery(
	String keyword,
	String lDongRegnCd,
	String lDongSignguCd,
	int pageNo,
	int numOfRows
) {
}
