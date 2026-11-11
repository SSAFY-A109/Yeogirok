package com.ssafy.yeogirok.domain.chat.push;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 채팅 푸시 운영 수치 등록. */
@Configuration
@EnableConfigurationProperties(ChatPushProperties.class)
public class ChatPushConfig {
}
