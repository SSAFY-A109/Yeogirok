package com.ssafy.yeogirok.domain.record.dto;

import java.time.OffsetDateTime;

/** API-091 검사를 마친 요청. merchantName 은 앞뒤 공백을 지운 값, paidAt 은 +09. */
public record MaterialReviewCommand(int version, String merchantName, OffsetDateTime paidAt, VisitPlaceInput place) {
}
