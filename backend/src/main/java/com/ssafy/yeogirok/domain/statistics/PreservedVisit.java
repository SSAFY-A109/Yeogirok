package com.ssafy.yeogirok.domain.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * footprint_statistics 의 한 행에 더할 탈퇴자 보존분.
 *
 * @param cellId        그 집계 버전의 기본 셀 "{위도 칸}:{경도 칸}"
 * @param statDate      방문 시각의 Asia/Seoul 날짜
 * @param categoryCodes 중복 없이 정렬한 카테고리 코드의 JSON 배열 문자열
 * @param visitCount    더할 발자취 수(1 이상)
 */
public record PreservedVisit(
	String aggregationVersion,
	String cellId,
	LocalDate statDate,
	String categoryCodes,
	BigDecimal centerLatitude,
	BigDecimal centerLongitude,
	long visitCount
) {
}
