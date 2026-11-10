package com.ssafy.yeogirok.domain.chat.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.chat.dto.ChatMessage;
import com.ssafy.yeogirok.domain.chat.dto.ChatMessagePage;
import com.ssafy.yeogirok.domain.chat.entity.RoomMessage;
import com.ssafy.yeogirok.domain.chat.repository.ChatAccessRow;
import com.ssafy.yeogirok.domain.chat.repository.MessageView;
import com.ssafy.yeogirok.domain.chat.repository.RoomMessageRepository;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.domain.UserStatus;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 메시지 저장(WebSocket SEND_MESSAGE)과 이력 조회(API-076).
 *
 * <p>저장은 채팅 전용 잠금({@link ChatRoomLock}, 여행방 행 잠금과 별개)을 잡은 뒤 ID 발급·INSERT·커밋 순서로 방마다 한 줄로
 * 세운다. 커밋된 메시지만 전달하도록 저장 완료 응답과 방송은 이 트랜잭션이 끝난 뒤 호출한 쪽이 한다.
 *
 * <p>권한(참여·방 삭제)은 잠금 없이 읽어 확인한다. 나가기와 거의 동시에 보낸 메시지는 나가기 직전 메시지로 저장될 수 있다
 * (나간 뒤 보낸 메시지는 막힌다).
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

	private final RoomMessageRepository roomMessageRepository;
	private final UserRepository userRepository;
	private final RoomFinder roomFinder;
	private final ChatReadStates chatReadStates;
	private final ChatRoomLock chatRoomLock;
	private final Clock clock;

	/**
	 * SEND_MESSAGE. 같은 clientMessageId·같은 본문의 재전송은 새로 저장하지 않고 원래 메시지를 돌려준다(replayed).
	 * 같은 clientMessageId·다른 본문은 C005.
	 */
	@Transactional
	public SendResult send(Long userId, Long roomId, String clientMessageId, String body) {
		validateClientMessageId(clientMessageId);
		validateBody(body);
		chatRoomLock.lock(roomId);
		requireAccess(userId, roomId);

		Optional<RoomMessage> existing = roomMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(roomId, userId,
			clientMessageId);
		if (existing.isPresent()) {
			if (!existing.get().getBody().equals(body)) {
				throw new BusinessException(ExceptionType.IDEMPOTENCY_KEY_REUSED, "같은 메시지 ID에 다른 본문을 사용할 수 없습니다.");
			}
			return new SendResult(view(existing.get().getId()), true);
		}
		RoomMessage saved = roomMessageRepository.saveAndFlush(
			new RoomMessage(roomId, userId, clientMessageId, body, clock.instant()));
		return new SendResult(view(saved.getId()), false);
	}

	/**
	 * WebSocket AUTH·PING 때 다시 확인한다. 계정 비활성 A003, 방 없음·삭제 RO001, 현재 참여자 아님 RO002.
	 *
	 * @return 방의 저장 완료 메시지 중 최신 ID. 없으면 null
	 */
	@Transactional(readOnly = true)
	public Long checkAccess(Long userId, Long roomId) {
		return requireAccess(userId, roomId).latestMessageId();
	}

	/** 방·참여 행·회원을 쿼리 하나로 확인한다. 실패하면 A003 → RO001 → RO002 순서로 알린다. */
	private ChatAccessRow requireAccess(Long userId, Long roomId) {
		Optional<ChatAccessRow> found = roomMessageRepository.findAccess(roomId, userId);
		if (found.isEmpty()) {
			// 참여한 적 없거나 방이 없다. 드문 경우라 기존 확인으로 오류 코드를 가린다(A003 → RO001 → RO002 순서).
			requireActiveUser(userId);
			roomFinder.getForMember(roomId, userId);
			throw new BusinessException(ExceptionType.ROOM_NOT_MEMBER);
		}
		ChatAccessRow row = found.get();
		if (row.userStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ExceptionType.ACCESS_TOKEN_INVALID, "탈퇴했거나 없는 회원입니다.");
		}
		if (row.roomDeletedAt() != null) {
			throw new BusinessException(ExceptionType.ROOM_NOT_FOUND);
		}
		if (row.leftAt() != null) {
			throw new BusinessException(ExceptionType.ROOM_NOT_MEMBER);
		}
		return row;
	}

	/**
	 * API-076. afterMessageId 가 없으면 과거 이력 모드(beforeMessageId 미만 또는 최근 limit 개), 있으면 재연결 복구 모드
	 * (afterMessageId 초과 throughMessageId 이하를 오래된 순으로). 복구 첫 페이지에서 throughMessageId 가 없으면 지금 방의
	 * 최신 ID(없으면 0)를 상한으로 고정해 돌려준다. 어느 모드든 items 는 오래된 → 최신 순이다.
	 */
	@Transactional(readOnly = true)
	public ChatMessagePage getMessages(Long userId, Long roomId, Long beforeMessageId, Long afterMessageId,
		Long throughMessageId, Integer limit) {
		int size = PageSize.resolve(limit);
		validateCursors(beforeMessageId, afterMessageId, throughMessageId);
		RoomMember member = roomFinder.getForMember(roomId, userId).member();

		ChatMessagePage page = afterMessageId == null
			? history(roomId, beforeMessageId, size)
			: replay(roomId, afterMessageId, throughMessageId, size);
		return new ChatMessagePage(page.items(), page.hasMore(), page.nextAfterMessageId(),
			page.replayThroughMessageId(), chatReadStates.of(member));
	}

	private ChatMessagePage history(Long roomId, Long beforeMessageId, int size) {
		List<MessageView> rows = beforeMessageId == null
			? roomMessageRepository.findLatest(roomId, Limit.of(size + 1))
			: roomMessageRepository.findBefore(roomId, beforeMessageId, Limit.of(size + 1));
		boolean hasMore = rows.size() > size;
		List<ChatMessage> items = (hasMore ? rows.subList(0, size) : rows).stream()
			.map(ChatMessage::from)
			.toList()
			.reversed();
		return new ChatMessagePage(items, hasMore, null, null, null);
	}

	private ChatMessagePage replay(Long roomId, long afterMessageId, Long throughMessageId, int size) {
		long through = throughMessageId != null ? throughMessageId : latestIdOrZero(roomId);
		if (through < afterMessageId) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "복구 상한이 afterMessageId 보다 작습니다.");
		}
		List<MessageView> rows = roomMessageRepository.findBetween(roomId, afterMessageId, through, Limit.of(size + 1));
		boolean hasMore = rows.size() > size;
		List<ChatMessage> items = (hasMore ? rows.subList(0, size) : rows).stream().map(ChatMessage::from).toList();
		Long nextAfter = hasMore ? items.getLast().id() : null;
		return new ChatMessagePage(items, hasMore, nextAfter, through, null);
	}

	private long latestIdOrZero(Long roomId) {
		Long latest = roomMessageRepository.findLatestId(roomId);
		return latest == null ? 0L : latest;
	}

	private MessageView view(Long messageId) {
		return roomMessageRepository.findViewById(messageId).orElseThrow();
	}

	private void requireActiveUser(Long userId) {
		if (!userRepository.findById(userId).map(User::isActive).orElse(false)) {
			throw new BusinessException(ExceptionType.ACCESS_TOKEN_INVALID, "탈퇴했거나 없는 회원입니다.");
		}
	}

	/** 조회 방향 혼용, afterMessageId 없는 throughMessageId, 범위 밖 ID 는 C003. */
	static void validateCursors(Long beforeMessageId, Long afterMessageId, Long throughMessageId) {
		if (beforeMessageId != null && afterMessageId != null) {
			throw notAllowed("beforeMessageId 와 afterMessageId 는 함께 보낼 수 없습니다.");
		}
		if (throughMessageId != null && afterMessageId == null) {
			throw notAllowed("throughMessageId 는 afterMessageId 와 함께 보냅니다.");
		}
		if (beforeMessageId != null && beforeMessageId <= 0) {
			throw notAllowed("beforeMessageId 는 양의 정수여야 합니다.");
		}
		if (afterMessageId != null && afterMessageId < 0) {
			throw notAllowed("afterMessageId 는 0 이상이어야 합니다.");
		}
		if (throughMessageId != null && throughMessageId < afterMessageId) {
			throw notAllowed("throughMessageId 는 afterMessageId 이상이어야 합니다.");
		}
	}

	/** 1~100자. 서버가 만든 값이 아니므로 형식은 따지지 않는다(UUID 권장). */
	static void validateClientMessageId(String clientMessageId) {
		int length = clientMessageId.codePointCount(0, clientMessageId.length());
		if (length == 0 || length > RoomMessage.CLIENT_MESSAGE_ID_MAX_LENGTH) {
			throw notAllowed("clientMessageId: 1~%d자여야 합니다.".formatted(RoomMessage.CLIENT_MESSAGE_ID_MAX_LENGTH));
		}
	}

	/**
	 * 빈 값·공백만인 값·100자 초과는 C003. 글자 수는 PostgreSQL char_length 와 같은 코드 포인트 기준이다.
	 * 넘는 본문을 잘라 저장하지 않고, 본문은 앞뒤 공백도 그대로 저장한다.
	 */
	static void validateBody(String body) {
		if (body.isBlank()) {
			throw notAllowed("body: 빈 본문이나 공백만인 본문은 보낼 수 없습니다.");
		}
		if (body.codePointCount(0, body.length()) > RoomMessage.BODY_MAX_LENGTH) {
			throw notAllowed("body: 최대 %d자입니다.".formatted(RoomMessage.BODY_MAX_LENGTH));
		}
	}

	private static BusinessException notAllowed(String message) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
	}

	/**
	 * @param replayed 같은 clientMessageId 재전송이라 새로 저장하지 않았다. 이때는 MESSAGE_CREATED 를 다시 방송하지 않는다
	 */
	public record SendResult(MessageView message, boolean replayed) {
	}
}
