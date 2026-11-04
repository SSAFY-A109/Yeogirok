package com.ssafy.yeogirok.domain.user.domain;

import java.time.Instant;

import org.springframework.util.StringUtils;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원. 식별 키는 {@code (provider, providerSubject)} 다.
 *
 * <p>이메일은 식별·계정 병합에 쓰지 않는다. 카카오는 동의 항목이라 없을 수 있고,
 * 이메일로 병합하면 다른 제공자의 미검증 이메일로 계정을 가로챌 수 있다.
 *
 * <p>컬럼은 ERD 초안의 users 테이블을 따른다(확정 전).
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	/** ID 토큰에 이름 클레임이 없을 때 쓰는 닉네임. nickname 이 NOT NULL 이라 둔다. */
	private static final String DEFAULT_NICKNAME = "여행자";
	private static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";
	/** 탈퇴 후 provider_subject 에 넣는 값의 접두어. 원래 sub 를 비워 같은 계정이 새 회원으로 가입할 수 있게 한다. */
	private static final String WITHDRAWN_SUBJECT_PREFIX = "withdrawn:";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false)
	private OAuthProvider provider;

	@Column(name = "provider_subject", nullable = false)
	private String providerSubject;

	@Column(name = "email", length = 320)
	private String email;

	@Column(name = "nickname", nullable = false, length = 50)
	private String nickname;

	@Column(name = "profile_image_url", columnDefinition = "TEXT")
	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private UserStatus status;

	@Column(name = "withdrawn_at")
	private Instant withdrawnAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	private User(OAuthProvider provider, String providerSubject, String email, String nickname,
		String profileImageUrl, Instant now) {
		this.provider = provider;
		this.providerSubject = providerSubject;
		this.email = email;
		this.nickname = StringUtils.hasText(nickname) ? nickname : DEFAULT_NICKNAME;
		this.profileImageUrl = profileImageUrl;
		this.status = UserStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static User signUp(OAuthProvider provider, String providerSubject, String email, String nickname,
		String profileImageUrl, Instant now) {
		return new User(provider, providerSubject, email, nickname, profileImageUrl, now);
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	/**
	 * API-009. 계정을 비활성화하고 개인정보를 지운다. 제공자 연결 해제에 쓸 원래 sub 는 호출 전에 따로 읽어 둔다.
	 *
	 * <p>(provider, provider_subject) 가 유니크라서 원래 sub 를 그대로 두면 같은 계정으로 다시 가입할 수 없다.
	 * 탈퇴 회원이 다시 로그인하면 새 회원으로 가입시키는 정책이라 sub 를 회원 id 기반 값으로 바꾼다.
	 */
	public void withdraw(Instant now) {
		this.status = UserStatus.WITHDRAWN;
		this.withdrawnAt = now;
		this.updatedAt = now;
		this.providerSubject = WITHDRAWN_SUBJECT_PREFIX + id;
		this.email = null;
		this.nickname = WITHDRAWN_NICKNAME;
		this.profileImageUrl = null;
	}

	/** ERD 에 role 컬럼이 없어 저장하지 않는다. 지금은 모든 회원이 USER 다(UserRole 참고). */
	public UserRole getRole() {
		return UserRole.USER;
	}
}
