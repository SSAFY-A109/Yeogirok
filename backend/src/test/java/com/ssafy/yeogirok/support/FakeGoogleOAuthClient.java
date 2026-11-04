package com.ssafy.yeogirok.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.yeogirok.global.oauth.client.GoogleOAuthClient;

/**
 * 구글 토큰 엔드포인트 대신 쓴다. {@link #register} 로 등록한 코드만 교환에 성공하고, 해제한 토큰을 기록한다.
 * {@link #failingRevoke} 를 켜면 해제가 실패한다.
 */
public class FakeGoogleOAuthClient extends GoogleOAuthClient {

	private final Map<String, String> idTokensByCode = new HashMap<>();
	private final List<String> revoked = new ArrayList<>();
	private boolean failingRevoke;

	public FakeGoogleOAuthClient() {
		super(FakeKakaoUnlinkClient.fakeProperties());
	}

	/** 이 코드를 교환하면 주어진 ID 토큰을 준다. 해제에 쓰는 리프레시 토큰은 {@code refresh-<code>} 다. */
	public void register(String code, String idToken) {
		idTokensByCode.put(code, idToken);
	}

	@Override
	public GoogleTokens exchange(String serverAuthCode) {
		String idToken = idTokensByCode.remove(serverAuthCode);
		if (idToken == null) {
			throw new IllegalStateException("invalid_grant(테스트)");
		}
		return new GoogleTokens("access-" + serverAuthCode, "refresh-" + serverAuthCode, idToken);
	}

	@Override
	public void revoke(String token) {
		if (failingRevoke) {
			throw new IllegalStateException("구글 해제 실패(테스트)");
		}
		revoked.add(token);
	}

	public List<String> revoked() {
		return revoked;
	}

	public void failingRevoke(boolean failingRevoke) {
		this.failingRevoke = failingRevoke;
	}

	public void reset() {
		idTokensByCode.clear();
		revoked.clear();
		failingRevoke = false;
	}
}
