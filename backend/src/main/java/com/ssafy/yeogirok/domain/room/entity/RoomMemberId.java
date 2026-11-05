package com.ssafy.yeogirok.domain.room.entity;

import java.io.Serializable;

/** room_members 복합 키 (room_id, user_id). */
public record RoomMemberId(Long roomId, Long userId) implements Serializable {
}
