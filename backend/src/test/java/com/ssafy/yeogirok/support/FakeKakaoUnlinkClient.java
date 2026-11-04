package com.ssafy.yeogirok.support;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ssafy.yeogirok.global.oauth.client.KakaoUnlinkClient;
import com.ssafy.yeogirok.global.oauth.client.UnlinkProperties;

/** 카카오 API 대신 호출을 기록한다. {@link #failing} 을 켜면 실패한다. */
public class FakeKakaoUnlinkClient extends KakaoUnlinkClient {

	// 동시 탈퇴 테스트에서 여러 요청 스레드가 함께 기록한다.
	private final List<String> unlinked = new CopyOnWriteArrayList<>();
	private boolean failing;

	public FakeKakaoUnlinkClient() {
		super(fakeProperties());
	}

	static UnlinkProperties fakeProperties() {
		URI localhost = URI.create("http://localhost");
		return new UnlinkProperties("fake", localhost, "fake", "fake", localhost, Duration.ofDays(1), 5);
	}

	@Override
	public void unlink(String kakaoUserId) {
		if (failing) {
			throw new IllegalStateException("카카오 연결 끊기 실패(테스트)");
		}
		unlinked.add(kakaoUserId);
	}

	public List<String> unlinked() {
		return unlinked;
	}

	public void failing(boolean failing) {
		this.failing = failing;
	}

	public void reset() {
		unlinked.clear();
		failing = false;
	}
}
