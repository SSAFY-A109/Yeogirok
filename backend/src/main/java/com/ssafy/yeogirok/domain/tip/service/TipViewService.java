package com.ssafy.yeogirok.domain.tip.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.dto.TipViewResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.ArTipReaction;
import com.ssafy.yeogirok.domain.tip.entity.ArTipReactionId;
import com.ssafy.yeogirok.domain.tip.repository.ArTipReactionRepository;
import com.ssafy.yeogirok.domain.tip.repository.ArTipReactionRepository.ReactionCounts;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;

import lombok.RequiredArgsConstructor;

/**
 * API-048 현장 팁 상세. 공개 팁 T001 → 5m T004 → 공간 참조 만료 T005 순서로 본다. 다른 사람 팁 사진의 URL 은 이 요청에서만
 * 발급한다(노션 048, 만료되면 이 API 를 다시 불러 현장 조건을 다시 본다).
 */
@Service
@RequiredArgsConstructor
public class TipViewService {

	private final PublicTipFinder publicTipFinder;
	private final TipPhotoUrls tipPhotoUrls;
	private final ArTipReactionRepository reactionRepository;
	private final UserRepository userRepository;
	private final LocationEvidencePolicy locationEvidencePolicy;
	private final Clock clock;

	@Transactional(readOnly = true)
	public TipViewResponse view(Long userId, Long tipId, LocationEvidence location) {
		Instant now = clock.instant();
		locationEvidencePolicy.verify(location, now);
		ArTip tip = publicTipFinder.getPublished(tipId);
		publicTipFinder.checkInteractable(tip, location);
		// DDL 은 만료 시각을 비울 수 있다(049 는 항상 채운다). 비어 있으면 만료로 보지 않는다.
		if (tip.getSpatialExpiresAt() != null && !tip.getSpatialExpiresAt().isAfter(now)) {
			throw new BusinessException(ExceptionType.TIP_SPATIAL_EXPIRED);
		}

		List<TipViewResponse.Photo> photos = tipPhotoUrls.issueFor(tipId)
			.map(photo -> new TipViewResponse.Photo(photo.imageId(), photo.url(), null))
			.stream().toList();
		TipViewResponse.Author author = userRepository.findById(tip.getAuthorId())
			.filter(User::isActive)
			.map(user -> new TipViewResponse.Author(user.getId(), user.getNickname()))
			.orElseGet(TipViewResponse.Author::withdrawn);
		ReactionCounts counts = reactionRepository.countReactions(tipId);
		TipViewResponse.Reactions reactions = new TipViewResponse.Reactions(counts.likes(), counts.dislikes(),
			reactionRepository.findById(new ArTipReactionId(tipId, userId)).map(ArTipReaction::getReaction).orElse(null));

		return new TipViewResponse(tip.getId(), tip.getCategory(), tip.getBody(), tip.getCreatedAt(),
			tip.getPublishedAt(), photos, author, new TipViewResponse.Location(tip.getLatitude(), tip.getLongitude()),
			tip.spatialReference(), tip.getPlacement(), reactions);
	}
}
