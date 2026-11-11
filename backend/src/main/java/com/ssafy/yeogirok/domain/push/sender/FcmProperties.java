package com.ssafy.yeogirok.domain.push.sender;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM HTTP v1 발송 설정. application.yaml {@code yeogirok.push.fcm}.
 *
 * @param serviceAccountBase64 Firebase 서비스 계정 키 JSON 파일 전체를 Base64 한 줄로 만든 값(환경 변수
 *                             {@code FIREBASE_SERVICE_ACCOUNT_BASE64}). 비어 있으면 푸시를 보내지 않는다
 * @param connectTimeout       FCM 연결 제한 시간
 * @param readTimeout          FCM 응답 제한 시간
 */
@ConfigurationProperties("yeogirok.push.fcm")
public record FcmProperties(String serviceAccountBase64, Duration connectTimeout, Duration readTimeout) {

	@Override
	public String toString() {
		// 키는 로그에 남기지 않는다.
		return "FcmProperties[configured=" + (serviceAccountBase64 != null && !serviceAccountBase64.isBlank())
			+ ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
	}
}
