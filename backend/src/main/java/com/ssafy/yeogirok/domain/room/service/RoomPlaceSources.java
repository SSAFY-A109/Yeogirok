package com.ssafy.yeogirok.domain.room.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.course.service.CourseAuthors;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.room.dto.SourceRecordResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;

import lombok.RequiredArgsConstructor;

/**
 * 후보의 원본 공개 코스(sourceRecord, API-028·022). 공개 코스 상세(API-016)·가져오기(API-030)와 같은 기준으로, 게시 중이고 원본
 * 방이 삭제되지 않은 코스만 돌려준다. 그렇지 않은 후보는 결과에 없다(응답의 sourceRecord 가 null).
 * 후보 수와 관계없이 기록·방·작성자를 각각 한 번씩 읽는다. 호출한 쪽 트랜잭션 안에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class RoomPlaceSources {

	private final TravelRecordRepository travelRecordRepository;
	private final TravelRoomRepository travelRoomRepository;
	private final CourseAuthors courseAuthors;

	/** @return 원본 기록 ID → 지금 볼 수 있는 원본 코스 */
	public Map<Long, SourceRecordResponse> of(Collection<RoomPlace> places) {
		Set<Long> recordIds = places.stream().map(RoomPlace::getSourceRecordId).filter(Objects::nonNull)
			.collect(Collectors.toSet());
		if (recordIds.isEmpty()) {
			return Map.of();
		}
		List<TravelRecord> published = travelRecordRepository.findAllById(recordIds).stream()
			.filter(record -> record.getPublishedAt() != null).toList();
		Set<Long> liveRoomIds = travelRoomRepository.findAllById(published.stream().map(TravelRecord::getRoomId)
				.collect(Collectors.toSet())).stream()
			.filter(room -> !room.isDeleted()).map(TravelRoom::getId).collect(Collectors.toSet());
		Map<Long, List<PublicAuthor>> authors = courseAuthors.byRoom(liveRoomIds);
		return published.stream().filter(record -> liveRoomIds.contains(record.getRoomId()))
			.collect(Collectors.toMap(TravelRecord::getId, record -> new SourceRecordResponse(record.getId(),
				record.getTitle(), authors.getOrDefault(record.getRoomId(), List.of())), (a, b) -> a));
	}
}
