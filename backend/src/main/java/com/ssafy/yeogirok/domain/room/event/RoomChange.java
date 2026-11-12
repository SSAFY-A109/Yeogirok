package com.ssafy.yeogirok.domain.room.event;

/**
 * 여행방에서 바뀐 부분. 앱은 이 값으로 다시 받을 조회를 고른다({@link RoomChangedEvent}).
 */
public enum RoomChange {

	/** 방 정보(제목·날짜·이동 수단). API-022 를 다시 받는다. */
	ROOM,
	/** 후보 장소 목록과 일정 재계산 필요 표시. API-022·028 을 다시 받는다. */
	PLACES,
	/** 확정 일정. API-022·036 을 다시 받는다. */
	SCHEDULE,
	/** 현재 참여자(참여·나가기). 여행방 version 은 오르지 않는다. API-022 를 다시 받는다. */
	MEMBERS
}
