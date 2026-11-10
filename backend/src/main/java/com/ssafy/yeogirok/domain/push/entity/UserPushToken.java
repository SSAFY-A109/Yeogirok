package com.ssafy.yeogirok.domain.push.entity;

import java.time.Instant;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Android 푸시 기기 토큰(노션 ERD DDL 20번 {@code user_push_tokens}). 회원 1명에 여러 기기 토큰을 둘 수 있고, FCM 토큰
 * 하나는 한 계정에만 연결한다(토큰이 기본키).
 *
 * <p>DDL 과 다른 점: FK({@code user_id → users ON DELETE CASCADE})는 다른 엔티티처럼 Flyway V1 에 맡기고, 시각은 DB 기본값
 * 대신 서비스 시계로 넣는다. 탈퇴는 회원 행을 지우지 않으므로 토큰은 탈퇴 트랜잭션에서 직접 지운다.
 */
@Getter
@Entity
@Table(name = "user_push_tokens",
	indexes = @Index(name = "ix_user_push_tokens_user", columnList = "user_id"),
	check = {
		@CheckConstraint(name = "user_push_tokens_token_check", constraint = "btrim(token) <> ''"),
		@CheckConstraint(name = "user_push_tokens_platform_check", constraint = "platform = 'ANDROID'")
	})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPushToken implements Persistable<String> {

	/** 서버가 받는 토큰 최대 길이(노션에 없는 서버 규칙). FCM 토큰은 보통 200자 안팎이다. */
	public static final int TOKEN_MAX_LENGTH = 4096;

	@Id
	@Column(columnDefinition = "TEXT")
	private String token;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "TEXT")
	private PushPlatform platform;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** 마지막으로 등록(API-095)한 시각. */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * 새로 만든 행인지. 기본키를 직접 넣으므로 이것이 없으면 저장할 때 merge(조회 후 덮어쓰기)가 된다. 새 행은 INSERT 로만 넣어
	 * 같은 토큰의 동시 첫 등록이 기본키 충돌로 드러나게 한다.
	 */
	@Transient
	private boolean newRow;

	public UserPushToken(String token, Long userId, PushPlatform platform, Instant now) {
		this.token = token;
		this.userId = userId;
		this.platform = platform;
		this.createdAt = now;
		this.updatedAt = now;
		this.newRow = true;
	}

	/** 같은 토큰을 다시 등록했다. 다른 계정의 토큰이었으면 지금 계정으로 옮긴다(같은 기기에서 계정 변경). */
	public void registerTo(Long userId, PushPlatform platform, Instant now) {
		this.userId = userId;
		this.platform = platform;
		this.updatedAt = now;
	}

	@Override
	public String getId() {
		return token;
	}

	@Override
	public boolean isNew() {
		return newRow;
	}

	@PostLoad
	@PostPersist
	void markPersisted() {
		this.newRow = false;
	}
}
