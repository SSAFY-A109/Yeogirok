package com.ssafy.yeogirok.domain.room.event;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 여행방 내용이 바뀐 트랜잭션에서 발행한다. 커밋된 뒤에만 채팅 연결로 {@code ROOM_UPDATED} 를 보낸다(롤백되면 보내지 않는다).
 * 바뀐 데이터는 싣지 않는다. 앱이 기존 조회 API 로 다시 받아 권한 확인을 한 곳(조회)에만 둔다.
 *
 * @param roomId    여행방
 * @param changedBy 바꾼 사람. 앱이 자기 요청의 알림을 가려낼 때 쓴다
 * @param version   커밋된 뒤의 여행방 version. 참여·나가기는 version 을 올리지 않아 지금 값 그대로다
 * @param changes   바뀐 부분. 하나 이상
 */
public record RoomChangedEvent(Long roomId, Long changedBy, int version, Set<RoomChange> changes) {

	public RoomChangedEvent {
		if (changes.isEmpty()) {
			throw new IllegalArgumentException("changes 가 비었습니다.");
		}
		// 선언 순서(ROOM, PLACES, SCHEDULE, MEMBERS)로 돈다. 프레임의 changes 도 이 순서다.
		changes = Collections.unmodifiableSet(EnumSet.copyOf(changes));
	}

	public static RoomChangedEvent of(Long roomId, Long changedBy, int version, RoomChange first, RoomChange... rest) {
		return new RoomChangedEvent(roomId, changedBy, version, EnumSet.of(first, rest));
	}
}
