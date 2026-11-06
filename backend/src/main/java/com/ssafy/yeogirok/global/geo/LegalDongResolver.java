package com.ssafy.yeogirok.global.geo;

import java.math.BigDecimal;

/** 좌표 → 법정동. 판정하지 못하면(결과 없음·실패·키 없음) 예외 없이 {@link LegalDong#EMPTY} 를 돌려준다. */
public interface LegalDongResolver {

	LegalDong resolve(BigDecimal latitude, BigDecimal longitude);
}
