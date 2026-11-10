package com.ssafy.yeogirok.domain.record.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.dto.RecordDetailResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordListResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.record.repository.VisitRegion;
import com.ssafy.yeogirok.domain.record.service.RecordFinder.RecordRoom;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.record.material.MaterialQueryService;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberView;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.request.PageSize;

import lombok.RequiredArgsConstructor;

/** 여행 기록 조회(API-058·059). */
@Service
@RequiredArgsConstructor
public class RecordQueryService {

	private final RecordFinder recordFinder;
	private final RoomFinder roomFinder;
	private final RecordVisitViews recordVisitViews;
	private final TravelRecordRepository travelRecordRepository;
	private final TravelRoomRepository travelRoomRepository;
	private final RoomMemberRepository roomMemberRepository;
	private final ActualVisitRepository actualVisitRepository;
	private final MaterialQueryService materialQueryService;
	private final RecordProgressReader recordProgressReader;

	/**
	 * API-058. 현재 참여 중이고 방이 삭제되지 않은 기록을 최근 생성 순(id 내림차순)으로(명세 5절 2).
	 * limit 은 한 묶음 크기(기본 20·최대 50, C007), 나머지는 nextCursor 로 이어 받는다(C006).
	 * roomId 를 주면 그 방 참여를 먼저 확인한다(RO001·RO002). 참여자·방문은 기록마다 따로 읽지 않고 한 번에 읽는다.
	 */
	@Transactional(readOnly = true)
	public RecordListResponse getList(Long userId, Long roomId, RecordStatus status, String cursor, Integer limit) {
		int size = PageSize.resolve(limit);
		RecordCursor after = RecordCursor.decodeOrNull(cursor);
		if (roomId != null) {
			roomFinder.getForMember(roomId, userId);
		}
		List<TravelRecord> found = travelRecordRepository.findJoinedRecords(userId, roomId, status,
			after == null ? Long.MAX_VALUE : after.recordId(), Limit.of(size + 1));
		List<TravelRecord> page = found.size() > size ? found.subList(0, size) : found;
		if (page.isEmpty()) {
			return new RecordListResponse(List.of(), null);
		}

		List<Long> roomIds = page.stream().map(TravelRecord::getRoomId).toList();
		Map<Long, TravelRoom> rooms = travelRoomRepository.findAllById(roomIds).stream()
			.collect(toMap(TravelRoom::getId, Function.identity()));
		Map<Long, List<RoomMemberView>> members = roomMemberRepository.findCurrentMembers(roomIds).stream()
			.collect(groupingBy(RoomMemberView::roomId));
		Map<Long, List<VisitRegion>> visits = actualVisitRepository
			.findRegionsInOrder(page.stream().map(TravelRecord::getId).toList()).stream()
			.collect(groupingBy(VisitRegion::recordId));

		Map<Long, RecordProgress> progress = recordProgressReader.read(userId, page, visits.entrySet().stream()
			.collect(toMap(Map.Entry::getKey, entry -> entry.getValue().size())));
		List<RecordListResponse.Item> items = page.stream().map(record -> item(record, rooms.get(record.getRoomId()),
			members.getOrDefault(record.getRoomId(), List.of()), visits.getOrDefault(record.getId(), List.of()),
			progress.get(record.getId())))
			.toList();
		String nextCursor = found.size() > size ? new RecordCursor(page.getLast().getId()).encode() : null;
		return new RecordListResponse(items, nextCursor);
	}

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
		RecordProgress progress = recordProgressReader.read(userId, List.of(record), Map.of(recordId, board.visits().size()))
			.get(recordId);
		return new RecordDetailResponse(record.getId(), record.getTitle(), record.getStatus(), record.getRoomId(),
			members, room.getStartDate(), room.getEndDate(), progress, null, List.of(), board.photoPool(),
			board.visits(), materialQueryService.summaries(recordId, userId),
			record.getPublishedAt() == null ? null : record.getId(), record.getVersion());
	}

	private static RecordListResponse.Item item(TravelRecord record, TravelRoom room, List<RoomMemberView> members,
		List<VisitRegion> visits, RecordProgress progress) {
		RecordListResponse.Publication publication = record.getPublishedAt() == null ? null
			: new RecordListResponse.Publication(record.getId(), record.getPublishedAt(), record.getVersion());
		return new RecordListResponse.Item(record.getId(), record.getTitle(), record.getRoomId(), record.getStatus(),
			room.getStartDate(), room.getEndDate(), visits.isEmpty() ? null : visits.getFirst().regionName(),
			members.size(), members.stream()
				.map(member -> new RecordListResponse.Member(member.userId(), member.nickname(), member.profileImageUrl()))
				.toList(),
			visits.size(), progress, publication);
	}
}
