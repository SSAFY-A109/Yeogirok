package com.ssafy.yeogirok.domain.room.dto;

import java.util.List;
import java.util.Map;

import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;

/**
 * 코스에서 가져온 후보의 원본 공개 코스(API-028·022 의 sourceRecord). 지금 공개 중인 코스만 채운다. 게시 해제·원본 방 삭제로
 * 볼 수 없으면 sourceRecord 자체가 null 이다(비공개 제목·과거 스냅샷을 내보내지 않는다).
 *
 * @param id      원본 기록 ID(= courseId)
 * @param title   지금 코스 제목
 * @param authors 공개 코스 상세(API-016)와 같은 작성자 목록
 */
public record SourceRecordResponse(
	Long id,
	String title,
	List<PublicAuthor> authors
) {

	public SourceRecordResponse {
		authors = List.copyOf(authors);
	}

	/**
	 * 후보 하나의 원본 코스. 코스에서 가져온 후보가 아니거나 지금 볼 수 없으면 null.
	 *
	 * @param sourceRecords 원본 기록 ID → 지금 볼 수 있는 원본 코스({@code RoomPlaceSources})
	 */
	public static SourceRecordResponse of(RoomPlace place, Map<Long, SourceRecordResponse> sourceRecords) {
		return place.getSourceRecordId() == null ? null : sourceRecords.get(place.getSourceRecordId());
	}
}
