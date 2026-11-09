package com.ssafy.yeogirok.domain.tip.dto;

import com.ssafy.yeogirok.domain.tip.entity.TipStatus;

/**
 * API-049 202 응답. 처음 요청은 항상 REGISTERING 이고, 멱등키 재전송은 그때의 팁 상태를 준다.
 * @param jobId 재전송 때 작업 정보가 Redis 에 없으면 null
 */
public record TipCreateResponse(Long tipId, String jobId, TipStatus status) {
}
