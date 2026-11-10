package com.ssafy.yeogirok.domain.room.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberView;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;

/**
 * API-022 여행방 상세. places 는 빼지 않은 후보(담은 순서). schedule 은 API-036 과 같은 구조이고 일정이 없으면 null 이다.
 * 뺀 후보를 가리키는 일정 항목도 schedule 에는 그대로 있다. recordIds 는 여행 기록 API 를 만들기 전이라 빈 배열이다.
 */
public record RoomDetailResponse(
	Long id,
	String title,
	LocalDate startDate,
	LocalDate endDate,
	TransportMode transportMode,
	List<Member> members,
	List<Place> places,
	List<Long> recordIds,
	Integer version,
	ChatReadState chatReadState,
	ScheduleView schedule
) {

	public static RoomDetailResponse of(TravelRoom room, List<RoomMemberView> members, List<RoomPlace> places,
		Map<String, String> categoryNames, ChatReadState chatReadState, ScheduleView schedule) {
		return new RoomDetailResponse(room.getId(), room.getTitle(), room.getStartDate(), room.getEndDate(),
			room.getTransportMode(), members.stream().map(Member::from).toList(),
			places.stream().map(place -> Place.from(place, categoryNames)).toList(), List.of(), room.getVersion(),
			chatReadState, schedule);
	}

	/** sourceRecord 는 공개 코스가 아직 없어 항상 null 이다(API-028 과 같다). */
	public record Place(Long id, PlaceInfo place, PlaceSourceType sourceType, Long sourceRecordId,
		Object sourceRecord, Long addedBy) {

		static Place from(RoomPlace place, Map<String, String> categoryNames) {
			return new Place(place.getId(), PlaceInfo.from(place.getPlace(), categoryNames), place.getSourceType(),
				place.getSourceRecordId(), null, place.getAddedBy());
		}
	}

	public record Member(Long userId, String nickname, @JsonProperty("isCreator") boolean isCreator) {

		static Member from(RoomMemberView view) {
			return new Member(view.userId(), view.nickname(), view.creator());
		}
	}
}
