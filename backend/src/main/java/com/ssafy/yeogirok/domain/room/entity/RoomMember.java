package com.ssafy.yeogirok.domain.room.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행방·기록 공통 참여자. 노션 DDL {@code room_members} 와 맞춘다. 한 사람은 방마다 행 하나만 갖고,
 * 나가면 {@code left_at} 을 채우고 다시 들어오면 비운다.
 *
 * <p>DDL 과 다른 점: FK·생성자 부분 유니크 인덱스({@code uq_room_members_creator})는 H2 비호환이라 Flyway V1 에 맡긴다.
 */
@Getter
@Entity
@IdClass(RoomMemberId.class)
@Table(name = "room_members", check = {
	@CheckConstraint(name = "room_members_left_at_check", constraint = "left_at IS NULL OR left_at >= joined_at"),
	@CheckConstraint(name = "room_members_last_read_check",
		constraint = "last_read_message_id IS NULL OR last_read_message_id > 0")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember {

	@Id
	@Column(name = "room_id")
	private Long roomId;

	@Id
	@Column(name = "user_id")
	private Long userId;

	/** 최초 방 생성자 표시. 추가 권한은 없다. */
	@Column(name = "is_creator", nullable = false)
	private boolean creator;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	/** null 이면 현재 참여 중. */
	@Column(name = "left_at")
	private Instant leftAt;

	/** 이 방에서 마지막으로 읽은 메시지. 읽음 이력이 없으면 null. 채팅 API 에서 채운다. */
	@Column(name = "last_read_message_id")
	private Long lastReadMessageId;

	private RoomMember(Long roomId, Long userId, boolean creator, Instant joinedAt) {
		this.roomId = roomId;
		this.userId = userId;
		this.creator = creator;
		this.joinedAt = joinedAt;
	}

	public static RoomMember creator(Long roomId, Long userId, Instant now) {
		return new RoomMember(roomId, userId, true, now);
	}

	public static RoomMember joined(Long roomId, Long userId, Instant now) {
		return new RoomMember(roomId, userId, false, now);
	}

	public boolean isCurrent() {
		return leftAt == null;
	}

	public void leave(Instant now) {
		this.leftAt = now;
	}

	/** 나간 뒤 다시 들어온다. 생성자 표시와 읽음 위치는 그대로 둔다. */
	public void rejoin(Instant now) {
		this.joinedAt = now;
		this.leftAt = null;
	}
}
