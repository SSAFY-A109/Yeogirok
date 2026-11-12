package com.ssafy.yeogirok.domain.place.tour;

/** TourAPI 를 쓸 수 없다. 메시지에 인증키·요청 주소를 넣지 않는다. */
public class TourApiException extends RuntimeException {

	public TourApiException(String message) {
		super(message);
	}
}
