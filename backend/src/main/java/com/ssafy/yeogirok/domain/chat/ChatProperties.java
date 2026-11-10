package com.ssafy.yeogirok.domain.chat;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 채팅 운영 수치(11/10 결정). application.yaml {@code yeogirok.chat}.
 *
 * @param ticketTtl     API-077 연결 티켓 수명
 * @param authTimeout   연결 뒤 첫 AUTH 를 기다리는 시간. 넘기면 A001 로 끊는다
 * @param idleTimeout   인증된 연결에서 이 시간 동안 프레임이 없으면 끊는다(앱은 25초마다 PING)
 * @param sweepInterval 위 두 시간을 검사하는 주기
 * @param cleanup       방 삭제 뒤 메시지 정리
 */
@ConfigurationProperties("yeogirok.chat")
public record ChatProperties(
	Duration ticketTtl,
	Duration authTimeout,
	Duration idleTimeout,
	Duration sweepInterval,
	Cleanup cleanup
) {

	/**
	 * @param dueInterval        정리 대상 목록(Redis)을 처리하는 주기
	 * @param safetyScanInterval Redis 가 유실돼도 삭제된 방의 남은 메시지를 DB 에서 찾아 지우는 주기
	 * @param batchSize          한 트랜잭션에서 지우는 메시지 수
	 */
	public record Cleanup(Duration dueInterval, Duration safetyScanInterval, int batchSize) {
	}
}
