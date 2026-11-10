package com.ssafy.yeogirok.domain.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.chat.entity.RoomMessage;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, Long> {

	String VIEW = "select new com.ssafy.yeogirok.domain.chat.repository.MessageView("
		+ "m.id, m.senderId, u.nickname, u.status, m.body, m.createdAt, m.clientMessageId)"
		+ " from RoomMessage m, User u where u.id = m.senderId";

	Optional<RoomMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId,
		String clientMessageId);

	boolean existsByIdAndRoomId(Long id, Long roomId);

	@Query(VIEW + " and m.id = :id")
	Optional<MessageView> findViewById(@Param("id") Long id);

	/** API-076 최근 이력. 최신부터 고른다(응답은 서비스가 오래된 순으로 뒤집는다). */
	@Query(VIEW + " and m.roomId = :roomId order by m.id desc")
	List<MessageView> findLatest(@Param("roomId") Long roomId, Limit limit);

	/** API-076 이전 이력. {@code beforeId} 미만에서 최신부터 고른다. */
	@Query(VIEW + " and m.roomId = :roomId and m.id < :beforeId order by m.id desc")
	List<MessageView> findBefore(@Param("roomId") Long roomId, @Param("beforeId") Long beforeId, Limit limit);

	/** API-076 재연결 복구. {@code afterId} 초과 {@code throughId} 이하를 오래된 순으로. */
	@Query(VIEW + " and m.roomId = :roomId and m.id > :afterId and m.id <= :throughId order by m.id asc")
	List<MessageView> findBetween(@Param("roomId") Long roomId, @Param("afterId") Long afterId,
		@Param("throughId") Long throughId, Limit limit);

	/**
	 * AUTH·PING 권한 확인. 방·참여 행·회원과 최신 메시지 ID 를 쿼리 하나로 읽는다. 참여 행이 없거나 방이 없으면 비어 있다
	 * (그때만 오류 코드를 가리려고 따로 조회한다).
	 */
	@Query("select new com.ssafy.yeogirok.domain.chat.repository.ChatAccessRow(r.deletedAt, rm.leftAt, u.status,"
		+ " (select max(m.id) from RoomMessage m where m.roomId = r.id))"
		+ " from TravelRoom r, RoomMember rm, User u"
		+ " where r.id = :roomId and rm.roomId = r.id and rm.userId = :userId and u.id = rm.userId")
	Optional<ChatAccessRow> findAccess(@Param("roomId") Long roomId, @Param("userId") Long userId);

	/** 방의 저장 완료 메시지 중 최신 ID. 없으면 null. */
	@Query("select max(m.id) from RoomMessage m where m.roomId = :roomId")
	Long findLatestId(@Param("roomId") Long roomId);

	/**
	 * 안 읽음 점. {@code afterId}(읽은 위치, 이력이 없으면 0)보다 큰 타인 메시지가 하나라도 있는지.
	 * ID 차이를 개수로 쓰지 않는다.
	 */
	@Query("select count(m) > 0 from RoomMessage m"
		+ " where m.roomId = :roomId and m.senderId <> :userId and m.id > :afterId")
	boolean existsUnread(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("afterId") Long afterId);

	/** API-020 목록용 안 읽음 점. 주어진 방 중 요청자에게 안 읽은 타인 메시지가 있는 방. */
	@Query("select distinct m.roomId from RoomMessage m, RoomMember rm"
		+ " where rm.userId = :userId and rm.roomId in :roomIds and m.roomId = rm.roomId"
		+ " and m.senderId <> :userId and m.id > coalesce(rm.lastReadMessageId, 0)")
	List<Long> findRoomIdsWithUnread(@Param("userId") Long userId, @Param("roomIds") Collection<Long> roomIds);

	/** 삭제된 방 정리. 한 번에 지울 만큼만 ID 를 읽는다. */
	@Query("select m.id from RoomMessage m where m.roomId = :roomId order by m.id")
	List<Long> findIdsByRoomId(@Param("roomId") Long roomId, Limit limit);

	/**
	 * 정리 안전망. 삭제됐는데 메시지가 남은 방. 삭제된 방에서 출발해 방마다 (room_id, id) 인덱스로 메시지가 있는지만 본다
	 * (메시지 테이블 전체를 훑지 않는다).
	 */
	@Query("select r.id from TravelRoom r where r.deletedAt is not null"
		+ " and exists (select m.id from RoomMessage m where m.roomId = r.id) order by r.id")
	List<Long> findDeletedRoomIdsWithMessages(Limit limit);
}
