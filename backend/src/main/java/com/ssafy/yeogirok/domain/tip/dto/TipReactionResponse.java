package com.ssafy.yeogirok.domain.tip.dto;

import com.ssafy.yeogirok.domain.tip.entity.TipReaction;

/** API-052 응답. 반응이 없으면 myReaction 은 null. */
public record TipReactionResponse(Long tipId, TipReaction myReaction, long likes, long dislikes) {
}
