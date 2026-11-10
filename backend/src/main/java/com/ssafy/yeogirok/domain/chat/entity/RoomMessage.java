package com.ssafy.yeogirok.domain.chat.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행방 채팅 메시지. 노션 DDL {@code room_messages} 와 맞춘다. 여행방 자체가 채팅 단위라 채팅방 테이블은 따로 없다.
 *
 * <p>같은 사람이 같은 방에서 같은 {@code client_message_id} 로 다시 보낸 메시지는 유니크 제약으로 하나만 남는다.
 * 저장은 방 행 잠금 아래에서 하므로 ID 순서가 곧 방 안의 저장 순서다.
 *
 * <p>DDL 과 다른 점: FK({@code room_id}, {@code sender_id}, {@code (room_id, sender_id) → room_members})는 다른 엔티티처럼
 * Flyway V1 에 맡긴다.
 */
@Getter
@Entity
@Table(name = "room_messages",
	uniqueConstraints = @UniqueConstraint(name = "uq_room_messages_client",
		columnNames = {"room_id", "sender_id", "client_message_id"}),
	indexes = @Index(name = "ix_room_messages_room_id", columnList = "room_id, id DESC"),
	check = @CheckConstraint(name = "room_messages_body_check", constraint = "char_length(body) <= 100"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMessage {

	/** 본문 최대 글자 수(코드 포인트, PostgreSQL char_length 와 같은 기준). */
	public static final int BODY_MAX_LENGTH = 100;
	public static final int CLIENT_MESSAGE_ID_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "sender_id", nullable = false)
	private Long senderId;

	@Column(name = "client_message_id", nullable = false, length = CLIENT_MESSAGE_ID_MAX_LENGTH)
	private String clientMessageId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** 값 검사는 서비스가 끝낸 뒤 부른다. 시각은 서비스 시계. */
	public RoomMessage(Long roomId, Long senderId, String clientMessageId, String body, Instant createdAt) {
		this.roomId = roomId;
		this.senderId = senderId;
		this.clientMessageId = clientMessageId;
		this.body = body;
		this.createdAt = createdAt;
	}
}
