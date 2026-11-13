package com.ssafy.yeogirok.domain.place.tour;

/** TourAPI 를 쓸 수 없다. 메시지에 인증키·요청 주소를 넣지 않는다. */
public class TourApiException extends RuntimeException {

	/** TourAPI 가 요청 파라미터 값이 잘못됐다고 거절할 때의 결과 코드(INVALID_REQUEST_PARAMETER_ERROR). */
	static final String INVALID_REQUEST_PARAMETER = "10";

	/** TourAPI 가 준 결과 코드. 호출·해석 실패처럼 결과 코드를 받지 못했으면 null. */
	private final String resultCode;

	public TourApiException(String message) {
		this(message, null);
	}

	public TourApiException(String message, String resultCode) {
		super(message);
		this.resultCode = resultCode;
	}

	public String getResultCode() {
		return resultCode;
	}

	/** 우리가 넘긴 값(검색어·콘텐츠 ID 등)을 TourAPI 가 받아들이지 않았다. 장애가 아니라 입력 문제다. */
	public boolean isInvalidRequest() {
		return INVALID_REQUEST_PARAMETER.equals(resultCode);
	}
}
