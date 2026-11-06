package com.ssafy.yeogirok.domain.schedule.travel;

import java.math.BigDecimal;

/** 이동 시간을 구할 지점(WGS84). */
public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
}
