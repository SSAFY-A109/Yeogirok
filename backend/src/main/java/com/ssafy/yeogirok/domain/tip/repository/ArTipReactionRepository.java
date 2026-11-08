package com.ssafy.yeogirok.domain.tip.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.tip.entity.ArTipReaction;
import com.ssafy.yeogirok.domain.tip.entity.ArTipReactionId;
import com.ssafy.yeogirok.domain.tip.entity.TipReaction;

public interface ArTipReactionRepository extends JpaRepository<ArTipReaction, ArTipReactionId> {

	long countByTipIdAndReaction(Long tipId, TipReaction reaction);

	/** API-048·052 반응 집계. 저장한 값 없이 행에서 바로 센다. */
	default ReactionCounts countReactions(Long tipId) {
		return new ReactionCounts(countByTipIdAndReaction(tipId, TipReaction.LIKE),
			countByTipIdAndReaction(tipId, TipReaction.DISLIKE));
	}

	record ReactionCounts(long likes, long dislikes) {
	}
}
