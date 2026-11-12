package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;
import java.util.List;

/** 검색어로 장소를 찾는다(카카오 키워드 검색). 실패해도 예외를 던지지 않고 결과 종류로 알린다. */
public interface PlaceKeywordSearch {

	Result search(String query);

	enum Outcome { FOUND, NOT_FOUND, FAILED, NO_KEY }

	/** @param roadAddress 도로명 주소. 없으면 null */
	record Place(String name, BigDecimal latitude, BigDecimal longitude, String roadAddress, String address) {
	}

	/**
	 * @param places  검색 결과(공급자 순서). FOUND 일 때만 비어 있지 않다
	 * @param failure FAILED 일 때 원인(HTTP 상태 코드·예외 종류). 검색어·키는 넣지 않는다
	 */
	record Result(Outcome outcome, List<Place> places, String failure) {

		public static final Result NOT_FOUND = new Result(Outcome.NOT_FOUND, List.of(), null);
		public static final Result NO_KEY = new Result(Outcome.NO_KEY, List.of(), null);

		public Result {
			places = List.copyOf(places);
		}

		public static Result found(List<Place> places) {
			return places.isEmpty() ? NOT_FOUND : new Result(Outcome.FOUND, places, null);
		}

		public static Result failed(String failure) {
			return new Result(Outcome.FAILED, List.of(), failure);
		}
	}
}
