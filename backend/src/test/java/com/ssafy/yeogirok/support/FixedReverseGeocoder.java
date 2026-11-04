package com.ssafy.yeogirok.support;

import java.math.BigDecimal;

import com.ssafy.yeogirok.global.geo.ReverseGeocodeResult;
import com.ssafy.yeogirok.global.geo.ReverseGeocoder;

/** 실제 카카오를 부르지 않고 정해 둔 값을 돌려주는 역지오코딩. 호출 횟수를 센다. */
public class FixedReverseGeocoder implements ReverseGeocoder {

	private ReverseGeocodeResult result;
	private int calls;

	public FixedReverseGeocoder(ReverseGeocodeResult result) {
		this.result = result;
	}

	/** 돌려줄 값을 바꾸고 호출 횟수를 0 으로 되돌린다. 빈이 테스트 사이에 재사용되므로 각 테스트 시작에 부른다. */
	public void willReturn(ReverseGeocodeResult result) {
		this.result = result;
		this.calls = 0;
	}

	public int calls() {
		return calls;
	}

	@Override
	public ReverseGeocodeResult reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
		calls++;
		return result;
	}
}
