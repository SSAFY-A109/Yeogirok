package com.ssafy.yeogirok.domain.chat.push;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 채팅 푸시 운영 수치. application.yaml {@code yeogirok.chat.push}. 발송 규칙은 노션 API-077 "채팅 푸시 알림 (Android)".
 *
 * @param presenceTtl   READY 연결 기록(Redis {@code chat:online:{roomId}})의 수명. PING 마다 늘린다. 연결이 닫히면 바로 지우므로
 *                      서버가 죽어 지우지 못했을 때만 쓰인다. 무응답 기준(idle-timeout)보다 길게 둔다
 * @param throttle      같은 수신자·같은 방에 다시 보내기까지의 간격(노션 3초, Redis {@code push:chat:{userId}:{roomId}})
 * @param previewLength 알림 본문에 보일 메시지 앞 글자 수(코드 포인트, 노션 40자). 넘으면 자르고 "…" 를 붙인다
 * @param threads       발송 스레드 수. WebSocket 처리 스레드가 아니라 여기서 보낸다
 * @param queueCapacity 발송 대기 줄 크기. 넘치면 그 메시지의 푸시를 버린다(메시지 저장·전달에는 영향 없음)
 */
@ConfigurationProperties("yeogirok.chat.push")
public record ChatPushProperties(
	Duration presenceTtl,
	Duration throttle,
	int previewLength,
	int threads,
	int queueCapacity
) {
}
