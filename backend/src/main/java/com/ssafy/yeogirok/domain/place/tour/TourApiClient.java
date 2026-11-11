package com.ssafy.yeogirok.domain.place.tour;

import java.util.List;

/** TourAPI 호출. 테스트에서는 가짜 구현으로 바꾼다. */
public interface TourApiClient {

	/**
	 * 지역 기반 관광정보 조회. 결과가 없으면 빈 목록이다.
	 *
	 * @throws TourApiException 키가 없거나, 호출·응답 해석에 실패했거나, TourAPI 가 오류 코드를 돌려줬을 때
	 */
	List<TourPlace> areaBasedList(TourAreaQuery query);
}
