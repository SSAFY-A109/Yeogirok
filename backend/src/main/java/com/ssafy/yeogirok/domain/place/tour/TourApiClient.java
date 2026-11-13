package com.ssafy.yeogirok.domain.place.tour;

import java.util.List;
import java.util.Optional;

/**
 * TourAPI 호출. 테스트에서는 가짜 구현으로 바꾼다. 세 메서드 모두 키가 없거나, 호출·응답 해석에 실패했거나,
 * TourAPI 가 오류 코드를 돌려주면 {@link TourApiException} 을 던진다.
 */
public interface TourApiClient {

	/** 지역 기반 관광정보 조회(areaBasedList2). 결과가 없으면 빈 목록이다. */
	List<TourPlace> areaBasedList(TourAreaQuery query);

	/** 키워드 검색(searchKeyword2). 결과가 없으면 빈 목록·0건이다. */
	TourSearchPage searchKeyword(TourKeywordQuery query);

	/** 공통 정보 조회(detailCommon2). 없는 콘텐츠이거나 좌표가 없으면 비어 있다. */
	Optional<TourPlace> detail(String contentId);
}
