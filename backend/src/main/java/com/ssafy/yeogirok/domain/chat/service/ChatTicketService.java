package com.ssafy.yeogirok.domain.chat.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.chat.ChatProperties;
import com.ssafy.yeogirok.domain.chat.dto.ChatTicketResponse;
import com.ssafy.yeogirok.domain.chat.realtime.ChatTicketStore;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;

import lombok.RequiredArgsConstructor;

/**
 * API-077 연결 티켓 발급. 사용자·방에 묶인 1회용이고 수명이 짧다. 연결할 때 다시 참여·방 삭제를 확인하므로 티켓이 있다고
 * 권한이 늘어나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatTicketService {

	public static final String CONNECT_PATH = "/ws/v1";

	private final ChatTicketStore ticketStore;
	private final RoomFinder roomFinder;
	private final ChatProperties properties;
	private final Clock clock;

	public ChatTicketResponse issue(Long userId, Long roomId) {
		roomFinder.getForMember(roomId, userId);
		String ticket = UUID.randomUUID().toString();
		Instant expiresAt = clock.instant().plus(properties.ticketTtl());
		ticketStore.save(ticket, userId, roomId, properties.ticketTtl());
		return new ChatTicketResponse(ticket, expiresAt, CONNECT_PATH, roomId);
	}

	/**
	 * 같은 멱등키로 다시 보낸 요청에 처음 발급한 티켓을 돌려준다. 그사이 나갔거나 방이 삭제됐으면 RO002·RO001.
	 * 이미 쓰거나 만료된 티켓일 수 있다. 그러면 AUTH 가 A003 이고, 앱은 새 멱등키로 새 티켓을 받는다.
	 *
	 * @param resultId {@link #resultIdOf} 로 만든 값
	 */
	public ChatTicketResponse getIssued(Long userId, Long roomId, String resultId) {
		roomFinder.getForMember(roomId, userId);
		int separator = resultId.indexOf('|');
		return new ChatTicketResponse(resultId.substring(0, separator),
			Instant.ofEpochMilli(Long.parseLong(resultId.substring(separator + 1))), CONNECT_PATH, roomId);
	}

	/** 멱등키 결과 ID. 티켓과 만료 시각을 함께 담아 재요청에 같은 응답을 돌려준다. */
	public static String resultIdOf(ChatTicketResponse response) {
		return response.ticket() + "|" + response.expiresAt().toEpochMilli();
	}
}
