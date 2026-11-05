package com.ssafy.yeogirok.domain.room.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberView;

/**
 * API-022 여행방 상세. places·scheduleId·recordIds 는 후보 장소·일정·여행 기록 API 를 만들기 전이라
 * 빈 배열·null·빈 배열이다.
 */
public record RoomDetailResponse(
	Long id,
	String title,
	LocalDate startDate,
	LocalDate endDate,
	TransportMode transportMode,
	List<Member> members,
	List<Object> places,
	Long scheduleId,
	List<Long> recordIds,
	Integer version,
	ChatReadState chatReadState
) {

	public static RoomDetailResponse of(TravelRoom room, List<RoomMemberView> members, RoomMember requester) {
		return new RoomDetailResponse(room.getId(), room.getTitle(), room.getStartDate(), room.getEndDate(),
			room.getTransportMode(), members.stream().map(Member::from).toList(), List.of(), null, List.of(),
			room.getVersion(), ChatReadState.of(requester.getLastReadMessageId()));
	}

	public record Member(Long userId, String nickname, @JsonProperty("isCreator") boolean isCreator) {

		static Member from(RoomMemberView view) {
			return new Member(view.userId(), view.nickname(), view.creator());
		}
	}
}
