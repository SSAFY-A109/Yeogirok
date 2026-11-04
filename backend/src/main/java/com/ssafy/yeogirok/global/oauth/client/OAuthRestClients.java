package com.ssafy.yeogirok.global.oauth.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 카카오·구글 API 를 부르는 RestClient. 기본 RestClient 는 제한 시간이 없어 상대 서버가 응답하지 않으면
 * 탈퇴 요청이나 재시도 스케줄러가 끝없이 기다린다. 시간을 넘기면 호출한 쪽의 실패 처리(카카오 재시도, 구글 로그)로 간다.
 */
final class OAuthRestClients {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private OAuthRestClients() {
	}

	static RestClient create(String baseUrl) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
			HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		requestFactory.setReadTimeout(READ_TIMEOUT);

		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}
}
