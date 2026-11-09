package com.ssafy.yeogirok.domain.tip.dto;

import com.ssafy.yeogirok.domain.tip.entity.TipStatus;

/** API-055 응답. status 는 항상 DELETED. */
public record TipDeleteResponse(Long tipId, TipStatus status) {
}
