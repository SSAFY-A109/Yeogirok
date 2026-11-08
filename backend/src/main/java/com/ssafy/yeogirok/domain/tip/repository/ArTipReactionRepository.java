package com.ssafy.yeogirok.domain.tip.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.tip.entity.ArTipReaction;
import com.ssafy.yeogirok.domain.tip.entity.ArTipReactionId;
import com.ssafy.yeogirok.domain.tip.entity.TipReaction;

public interface ArTipReactionRepository extends JpaRepository<ArTipReaction, ArTipReactionId> {

	long countByTipIdAndReaction(Long tipId, TipReaction reaction);
}
