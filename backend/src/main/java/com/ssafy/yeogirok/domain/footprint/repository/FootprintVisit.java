package com.ssafy.yeogirok.domain.footprint.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 탈퇴자 보존분 집계에 쓰는 발자취 열. 본문·주소 같은 나머지 열은 읽지 않는다. */
public record FootprintVisit(
	BigDecimal latitude,
	BigDecimal longitude,
	Instant visitedAt,
	List<String> categoryCodes
) {
}
