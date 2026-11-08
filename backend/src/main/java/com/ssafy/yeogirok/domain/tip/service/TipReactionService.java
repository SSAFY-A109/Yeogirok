package com.ssafy.yeogirok.domain.tip.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.dto.TipReactionRequest.TipReactionCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipReactionResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipReaction;
import com.ssafy.yeogirok.domain.tip.entity.ArTipReactionId;
import com.ssafy.yeogirok.domain.tip.entity.TipReaction;
import com.ssafy.yeogirok.domain.tip.repository.ArTipReactionRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;
import com.ssafy.yeogirok.global.lock.UserLock;

import lombok.RequiredArgsConstructor;

/**
 * API-052 반응 지정. 토글이 아니라 최종 상태 지정이다(같은 값 재전송은 유지, NONE 은 행 삭제·없어도 성공).
 * 공개 팁 T001 → 본인 팁 T009 → 5m T004 순서로 본다.
 */
@Service
@RequiredArgsConstructor
public class TipReactionService {

	private final PublicTipFinder publicTipFinder;
	private final ArTipReactionRepository reactionRepository;
	private final LocationEvidencePolicy locationEvidencePolicy;
	private final UserLock userLock;
	private final Clock clock;

	@Transactional
	public TipReactionResponse react(Long userId, Long tipId, TipReactionCommand command) {
		Instant now = clock.instant();
		locationEvidencePolicy.verify(command.location(), now);
		ArTip tip = publicTipFinder.getPublished(tipId);
		if (tip.getAuthorId().equals(userId)) {
			throw new BusinessException(ExceptionType.TIP_SELF_REACTION);
		}
		publicTipFinder.checkInteractable(tip, command.location());

		// 같은 사용자의 동시 요청을 한 줄로 세워 (tip_id, user_id) 행을 두 번 만들지 않게 한다. 집계는 행에서 바로 센다.
		userLock.lock(userId);
		ArTipReactionId id = new ArTipReactionId(tipId, userId);
		TipReaction reaction = command.reaction();
		reactionRepository.findById(id).ifPresentOrElse(
			existing -> {
				if (reaction == null) {
					reactionRepository.delete(existing);
				} else {
					existing.change(reaction, now);
				}
			},
			() -> {
				if (reaction != null) {
					reactionRepository.save(ArTipReaction.of(tipId, userId, reaction, now));
				}
			});
		reactionRepository.flush();
		return new TipReactionResponse(tipId, reaction,
			reactionRepository.countByTipIdAndReaction(tipId, TipReaction.LIKE),
			reactionRepository.countByTipIdAndReaction(tipId, TipReaction.DISLIKE));
	}
}
