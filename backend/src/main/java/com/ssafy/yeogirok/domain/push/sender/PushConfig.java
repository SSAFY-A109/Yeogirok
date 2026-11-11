package com.ssafy.yeogirok.domain.push.sender;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 푸시 발송기. 서비스 계정 키({@code FIREBASE_SERVICE_ACCOUNT_BASE64})가 있으면 FCM, 없으면 보내지 않는다.
 * 키가 없어도 앱은 뜬다(로컬·테스트, 서버에 키를 넣기 전).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(FcmProperties.class)
public class PushConfig {

	@Bean(destroyMethod = "close")
	public PushSender pushSender(FcmProperties properties) {
		if (!StringUtils.hasText(properties.serviceAccountBase64())) {
			log.warn("FCM 서비스 계정 키(FIREBASE_SERVICE_ACCOUNT_BASE64)가 없어 앱 푸시를 보내지 않습니다.");
			return new NoOpPushSender();
		}
		return FcmPushSender.create(properties);
	}
}
