package com.ssafy.yeogirok.domain.chat.cleanup;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.chat.ChatProperties;
import com.ssafy.yeogirok.domain.chat.repository.RoomMessageRepository;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 삭제된 여행방의 채팅 메시지 정리(노션 "방 삭제 커밋 직후 정리 대상 등록, 유예 없음, 비동기 재시도").
 * <ol>
 *   <li>정리 대상 목록(Redis): 방 메시지를 나눠 지우고 다 지운 뒤에만 목록에서 뺀다. 실패하면 다음 주기에 다시 한다.</li>
 *   <li>안전 스캔(DB): Redis 등록이 실패했거나 유실돼도 삭제됐는데 메시지가 남은 방을 찾아 지운다.</li>
 * </ol>
 * 삭제되지 않은 방의 메시지는 지우지 않는다(목록에 잘못 들어가도 지우지 않고 빼기만 한다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatCleanupJob {

	/** 안전 스캔 한 번에 볼 방 수. 남으면 다음 주기에 이어서 한다. */
	private static final int SCAN_ROOM_LIMIT = 100;

	private final ChatCleanupQueue cleanupQueue;
	private final RoomMessageRepository roomMessageRepository;
	private final TravelRoomRepository travelRoomRepository;
	private final TransactionTemplate transactionTemplate;
	private final ChatProperties properties;

	@Scheduled(fixedDelayString = "${yeogirok.chat.cleanup.due-interval}",
		initialDelayString = "${yeogirok.chat.cleanup.due-interval}")
	public void cleanQueued() {
		for (Long roomId : cleanupQueue.roomIds()) {
			try {
				deleteMessagesOfDeletedRoom(roomId);
				cleanupQueue.remove(roomId);
			} catch (RuntimeException e) {
				log.warn("채팅 메시지를 정리하지 못해 다음 주기에 다시 시도합니다. roomId={}", roomId, e);
			}
		}
	}

	@Scheduled(fixedDelayString = "${yeogirok.chat.cleanup.safety-scan-interval}",
		initialDelayString = "${yeogirok.chat.cleanup.safety-scan-interval}")
	public void scanDeletedRooms() {
		for (Long roomId : roomMessageRepository.findDeletedRoomIdsWithMessages(Limit.of(SCAN_ROOM_LIMIT))) {
			try {
				deleteMessagesOfDeletedRoom(roomId);
			} catch (RuntimeException e) {
				log.warn("채팅 안전 스캔 정리에 실패해 다음 주기에 다시 시도합니다. roomId={}", roomId, e);
			}
		}
	}

	private void deleteMessagesOfDeletedRoom(Long roomId) {
		boolean deleted = travelRoomRepository.findById(roomId).map(TravelRoom::isDeleted).orElse(true);
		if (!deleted) {
			log.warn("삭제되지 않은 방이 채팅 정리 대상에 있어 지우지 않고 뺍니다. roomId={}", roomId);
			return;
		}
		while (true) {
			List<Long> ids = roomMessageRepository.findIdsByRoomId(roomId, Limit.of(properties.cleanup().batchSize()));
			if (ids.isEmpty()) {
				return;
			}
			transactionTemplate.executeWithoutResult(status -> roomMessageRepository.deleteAllByIdInBatch(ids));
		}
	}
}
