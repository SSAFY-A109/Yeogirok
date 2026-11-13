package com.ssafy.yeogirok.global.geo;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 카카오 로컬 API 를 부르는 RestClient 설정(baseUrl·연결·응답 제한 시간)을 한 곳에서 만든다. */
final class KakaoLocalClients {

	private KakaoLocalClients() {
	}

	static RestClient.Builder builder(KakaoLocalProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory);
	}
}
