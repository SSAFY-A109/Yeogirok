package com.ssafy.yeogirok.domain.room.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class RoomInviteTokenCipherTest {

	/** application-test.yaml 과 같은 테스트 전용 키(Base64, 64바이트). */
	static final String KEY =
		"dGVzdC1vbmx5LXJvb20taW52aXRlLWtleS1mb3IteWVvZ2lyb2stdGVzdHMtMDEyMzQ1Njc4OWFiY2RlZmdoaQ==";
	static final String URL_BASE = "https://test.yeogirok/invite";

	RoomInviteTokenCipher cipher = new RoomInviteTokenCipher(new RoomInviteProperties(KEY, URL_BASE));

	@Test
	void 같은_토큰은_항상_같은_암호문이라_조회에_쓸_수_있다() {
		String token = cipher.newToken();

		assertThat(cipher.encrypt(token)).isEqualTo(cipher.encrypt(token));
		assertThat(cipher.encrypt(token)).isNotEqualTo(cipher.encrypt(cipher.newToken()));
	}

	@Test
	void 암호문을_복호화하면_원래_토큰이다() {
		String token = cipher.newToken();

		assertThat(cipher.decrypt(cipher.encrypt(token))).isEqualTo(token);
	}

	@Test
	void 암호문은_토큰_원문을_포함하지_않고_컬럼_길이_안에_들어간다() {
		String token = cipher.newToken();
		String encrypted = cipher.encrypt(token);

		assertThat(encrypted).doesNotContain(token).hasSizeLessThanOrEqualTo(255);
	}

	@Test
	void 새_토큰은_URL에_쓸_수_있는_43자다() {
		assertThat(cipher.newToken()).hasSize(RoomInviteTokenCipher.TOKEN_LENGTH).matches("[A-Za-z0-9_-]+");
	}

	@Test
	void 다른_키로는_복호화하지_못한다() {
		byte[] otherKey = new byte[64];
		otherKey[0] = 1;
		RoomInviteTokenCipher other = new RoomInviteTokenCipher(
			new RoomInviteProperties(Base64.getEncoder().encodeToString(otherKey), URL_BASE));
		String encrypted = cipher.encrypt(cipher.newToken());

		assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 키가_64바이트가_아니면_시작하지_않는다() {
		String shortKey = Base64.getEncoder().encodeToString(new byte[32]);

		assertThatThrownBy(() -> new RoomInviteTokenCipher(new RoomInviteProperties(shortKey, URL_BASE)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("64바이트");
	}
}
