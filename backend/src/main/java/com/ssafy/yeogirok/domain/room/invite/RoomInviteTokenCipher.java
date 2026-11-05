package com.ssafy.yeogirok.domain.room.invite;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.google.crypto.tink.DeterministicAead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.daead.AesSivKey;
import com.google.crypto.tink.daead.AesSivParameters;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import com.google.crypto.tink.util.SecretBytes;

/**
 * 초대 토큰 생성·암호화·복호화. 결정적 암호화(AES-SIV)라 같은 토큰은 항상 같은 암호문이 된다.
 * 그래서 {@code travel_rooms.invite_token_encrypted} 한 컬럼으로 토큰 조회(API-025)와 기존 링크 재표시(API-024)를 함께 한다.
 *
 * <p>키는 DB 밖(환경 변수)에 있어 DB 를 읽기만 한 사람은 토큰을 알 수 없다. 같은 원문이 같은 암호문이 되는 약점은
 * 토큰이 방마다 다른 32바이트 랜덤 값이라 의미가 없다.
 *
 * <p>키를 바꾸면 조회가 현재 키로 암호화해 비교하므로 저장된 값을 모두 새 키로 다시 암호화해야 한다. 링크 값은 바뀌지 않는다.
 */
@Component
public class RoomInviteTokenCipher {

	private static final int KEY_BYTES = 64;
	private static final int TOKEN_BYTES = 32;
	/** 생성한 토큰의 길이(32바이트 base64url). 이보다 긴 입력은 암호화하지 않고 바로 무효로 본다. */
	public static final int TOKEN_LENGTH = 43;
	/** 다른 용도의 암호문과 섞이지 않게 묶는 값. 바꾸면 저장된 값을 다시 암호화해야 한다. */
	private static final byte[] ASSOCIATED_DATA = "travel-room-invite".getBytes(StandardCharsets.UTF_8);

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final DeterministicAead aead;
	private final SecureRandom random = new SecureRandom();

	public RoomInviteTokenCipher(RoomInviteProperties properties) {
		this.aead = createAead(properties.tokenKey());
	}

	/** 새 초대 토큰. URL 에 그대로 쓸 수 있다. */
	public String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}

	public String encrypt(String token) {
		try {
			return ENCODER.encodeToString(aead.encryptDeterministically(token.getBytes(StandardCharsets.UTF_8),
				ASSOCIATED_DATA));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("초대 토큰을 암호화하지 못했습니다.", e);
		}
	}

	/** 저장된 값은 이 서버가 만든 것이라 복호화 실패는 키 설정 오류다. */
	public String decrypt(String encrypted) {
		try {
			return new String(aead.decryptDeterministically(DECODER.decode(encrypted), ASSOCIATED_DATA),
				StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("초대 토큰을 복호화하지 못했습니다. 암호화 키를 확인하세요.", e);
		}
	}

	private static DeterministicAead createAead(String base64Key) {
		byte[] key;
		try {
			key = Base64.getDecoder().decode(base64Key);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("yeogirok.room.invite.token-key 는 Base64 여야 합니다.", e);
		}
		if (key.length != KEY_BYTES) {
			throw new IllegalStateException(
				"yeogirok.room.invite.token-key 는 디코드했을 때 %d바이트여야 합니다(지금 %d바이트)."
					.formatted(KEY_BYTES, key.length));
		}
		try {
			DeterministicAeadConfig.register();
			AesSivParameters parameters = AesSivParameters.builder()
				.setKeySizeBytes(KEY_BYTES)
				.setVariant(AesSivParameters.Variant.NO_PREFIX)
				.build();
			AesSivKey sivKey = AesSivKey.builder()
				.setParameters(parameters)
				.setKeyBytes(SecretBytes.copyFrom(key, InsecureSecretKeyAccess.get()))
				.build();
			KeysetHandle handle = KeysetHandle.newBuilder()
				.addEntry(KeysetHandle.importKey(sivKey).withRandomId().makePrimary())
				.build();
			return handle.getPrimitive(RegistryConfiguration.get(), DeterministicAead.class);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("초대 토큰 암호화 키를 만들지 못했습니다.", e);
		}
	}
}
