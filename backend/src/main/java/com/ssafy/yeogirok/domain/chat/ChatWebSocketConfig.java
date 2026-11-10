package com.ssafy.yeogirok.domain.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.ssafy.yeogirok.domain.chat.realtime.ChatWebSocketHandler;
import com.ssafy.yeogirok.domain.chat.service.ChatTicketService;

import lombok.RequiredArgsConstructor;

/**
 * {@code /ws/v1} 채팅 WebSocket 등록. 인증은 HTTP 단계가 아니라 첫 AUTH 프레임의 1회용 티켓으로 한다(SecurityConfig 에서
 * 이 경로를 연다). Origin 은 검사하지 않는다(11/10 결정): 모바일 앱은 Origin 이 없거나 일정하지 않고, 쿠키가 아니라 티켓으로
 * 인증하므로 다른 사이트가 사용자 대신 연결할 수 없다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketConfigurer {

	private final ChatWebSocketHandler chatWebSocketHandler;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(chatWebSocketHandler, ChatTicketService.CONNECT_PATH).setAllowedOriginPatterns("*");
	}
}
