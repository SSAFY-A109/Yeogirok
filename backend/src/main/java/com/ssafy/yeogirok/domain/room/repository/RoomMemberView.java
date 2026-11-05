package com.ssafy.yeogirok.domain.room.repository;

/** 현재 참여자 한 명과 회원 표시 정보. */
public record RoomMemberView(
	Long roomId,
	Long userId,
	String nickname,
	String profileImageUrl,
	boolean creator,
	Long lastReadMessageId
) {
}
