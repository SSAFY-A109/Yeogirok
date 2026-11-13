package com.ssafy.yeogirok.domain.place.tour;

import java.util.List;

/**
 * 키워드 검색 한 쪽.
 *
 * @param places     좌표가 있는 항목만. 그래서 한 쪽 개수보다 적을 수 있다
 * @param totalCount TourAPI 가 준 전체 결과 수(좌표 없는 항목 포함)
 */
public record TourSearchPage(List<TourPlace> places, int totalCount) {

	public TourSearchPage {
		places = List.copyOf(places);
	}
}
