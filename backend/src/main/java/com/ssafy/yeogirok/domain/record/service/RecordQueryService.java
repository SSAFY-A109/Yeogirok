package com.ssafy.yeogirok.domain.record.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.dto.RecordDetailResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.service.RecordFinder.RecordRoom;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;

import lombok.RequiredArgsConstructor;

/** 여행 기록 조회(API-059). */
@Service
@RequiredArgsConstructor
public class RecordQueryService {

	private final RecordFinder recordFinder;
	private final RecordVisitViews recordVisitViews;
	private final RoomMemberRepository roomMemberRepository;

	/** 기록 없음·방 삭제 RE001, 현재 참여자 아님 RE002. */
	@Transactional(readOnly = true)
	public RecordDetailResponse getDetail(Long userId, Long recordId) {
		RecordRoom found = recordFinder.getReadable(recordId, userId);
		TravelRecord record = found.record();
		TravelRoom room = found.room();
		RecordVisitViews.Board board = recordVisitViews.load(recordId);
		List<RecordDetailResponse.Member> members = roomMemberRepository.findCurrentMembers(List.of(room.getId()))
			.stream().map(member -> new RecordDetailResponse.Member(member.userId(), member.nickname(), member.creator()))
			.toList();
		return new RecordDetailResponse(record.getId(), record.getTitle(), record.getStatus(), record.getRoomId(),
			members, room.getStartDate(), room.getEndDate(),
			RecordProgress.of(record.getStatus(), board.visits().size()), null, List.of(), board.photoPool(),
			board.visits(), List.of(), record.getPublishedAt() == null ? null : record.getId(), record.getVersion());
	}
}
