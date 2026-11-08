package com.ssafy.yeogirok.domain.tip.entity;

import java.io.Serializable;

/** ar_tip_reactions 복합 키 (tip_id, user_id). */
public record ArTipReactionId(Long tipId, Long userId) implements Serializable {
}
