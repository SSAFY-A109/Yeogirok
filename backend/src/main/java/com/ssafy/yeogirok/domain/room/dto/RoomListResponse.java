package com.ssafy.yeogirok.domain.room.dto;

import java.time.LocalDate;
import java.util.List;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberView;

/**
 * API-020 참여 여행방 목록. 다음 쪽이 없으면 nextCursor 는 null.
 * placeCount·recordIds 는 후보 장소·여행 기록 API 를 만들기 전이라 0·빈 배열이다.
 */
public record RoomListResponse(List<Item> items, String nextCursor) {

	public record Item(
		Long id,
		String title,
		LocalDate startDate,
		LocalDate endDate,
		TransportMode transportMode,
		int memberCount,
		List<Member> members,
		int placeCount,
		List<Long> recordIds,
		ChatReadState chatReadState
	) {

		/** @param members 이 방의 현재 참여자(요청자 포함) */
		public static Item of(TravelRoom room, List<RoomMemberView> members, Long requesterId) {
			Long lastRead = members.stream()
				.filter(member -> member.userId().equals(requesterId))
				.findFirst()
				.map(RoomMemberView::lastReadMessageId)
				.orElse(null);
			return new Item(room.getId(), room.getTitle(), room.getStartDate(), room.getEndDate(),
				room.getTransportMode(), members.size(), members.stream().map(Member::from).toList(), 0, List.of(),
				ChatReadState.of(lastRead));
		}
	}

	public record Member(Long userId, String nickname, String profileImageUrl) {

		static Member from(RoomMemberView view) {
			return new Member(view.userId(), view.nickname(), view.profileImageUrl());
		}
	}
}
