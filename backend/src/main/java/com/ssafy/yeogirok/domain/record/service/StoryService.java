package com.ssafy.yeogirok.domain.record.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.dto.StoryEditResponse;
import com.ssafy.yeogirok.domain.record.dto.StoryWriteResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitStory;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitStoryRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 방문별 이야기 작성·수정(API-069·070). 이야기는 참여자끼리만 보고 공개하지 않는다. 이야기마다 version 을 두지 않고
 * version 입력도 받지 않는다(노션 070). 저장하면 기록 version+1·DRAFT.
 */
@Service
@RequiredArgsConstructor
public class StoryService {

	private final RecordFinder recordFinder;
	private final ActualVisitRepository actualVisitRepository;
	private final VisitStoryRepository visitStoryRepository;
	private final Clock clock;

	/** API-069. 작성자는 인증 사용자, 같은 사람이 여러 글을 써도 된다. 쓰기 권한 RE001·RE003, 이 기록의 방문이 아니면 RE013. */
	@Transactional
	public StoryWriteResponse write(Long userId, Long recordId, Long visitId, String body) {
		TravelRecord record = recordFinder.lockWritable(recordId, userId).record();
		if (!actualVisitRepository.existsByIdAndRecordId(visitId, recordId)) {
			throw new BusinessException(ExceptionType.VISIT_NOT_FOUND);
		}
		Instant now = clock.instant();
		VisitStory story = visitStoryRepository.save(VisitStory.write(visitId, userId, body, now));
		record.markEdited(now);
		return new StoryWriteResponse(story.getId(), story.getAuthorId(), story.getBody(), record.getStatus());
	}

	/** 같은 멱등키 재전송. 현재 쓰기 권한을 다시 확인하고 그때 쓴 글과 지금 기록 상태를 준다. 그사이 지워졌으면 RE014. */
	@Transactional(readOnly = true)
	public StoryWriteResponse getWritten(Long userId, Long recordId, Long storyId) {
		TravelRecord record = recordFinder.checkWritable(recordId, userId);
		VisitStory story = visitStoryRepository.findById(storyId)
			.orElseThrow(() -> new BusinessException(ExceptionType.STORY_NOT_FOUND));
		return new StoryWriteResponse(story.getId(), story.getAuthorId(), story.getBody(), record.getStatus());
	}

	/**
	 * API-070. 쓰기 권한 RE001·RE003 → 이 기록의 그 방문에 있는 글이 아니면 RE014 → 작성자 본인이 아니면 RE015.
	 * 본문이 같으면 바꾸지 않고 기록 상태도 그대로 둔다.
	 */
	@Transactional
	public StoryEditResponse edit(Long userId, Long recordId, Long visitId, Long storyId, String body) {
		TravelRecord record = recordFinder.lockWritable(recordId, userId).record();
		VisitStory story = visitStoryRepository.findById(storyId)
			.filter(found -> found.getVisitId().equals(visitId))
			.filter(found -> actualVisitRepository.existsByIdAndRecordId(visitId, recordId))
			.orElseThrow(() -> new BusinessException(ExceptionType.STORY_NOT_FOUND));
		if (!story.getAuthorId().equals(userId)) {
			throw new BusinessException(ExceptionType.STORY_EDIT_FORBIDDEN);
		}
		Instant now = clock.instant();
		if (story.edit(body, now)) {
			record.markEdited(now);
		}
		return new StoryEditResponse(story.getId(), story.getBody(), story.getUpdatedAt(), record.getStatus());
	}
}
