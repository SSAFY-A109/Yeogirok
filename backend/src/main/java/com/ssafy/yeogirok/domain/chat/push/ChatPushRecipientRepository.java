package com.ssafy.yeogirok.domain.chat.push;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;

/** 채팅 푸시 받는 사람 조회(노션 API-077 채팅 푸시). 읽기 전용. */
public interface ChatPushRecipientRepository extends Repository<RoomMember, RoomMemberId> {

	/**
	 * 방이 살아 있고, 현재 참여 중이고(left_at IS NULL), 계정이 활성이며, 보낸 사람이 아니고, 등록 기기가 하나라도 있는 회원.
	 * 방 제목을 함께 읽는다(알림 제목). 접속 중인지는 여기서 보지 않는다({@link ChatPresence}).
	 */
	@Query("select new com.ssafy.yeogirok.domain.chat.push.ChatPushRecipient(rm.userId, r.title)"
		+ " from TravelRoom r, RoomMember rm, User u"
		+ " where r.id = :roomId and r.deletedAt is null"
		+ " and rm.roomId = r.id and rm.leftAt is null and rm.userId <> :senderId"
		+ " and u.id = rm.userId and u.status = com.ssafy.yeogirok.domain.user.domain.UserStatus.ACTIVE"
		+ " and exists (select 1 from UserPushToken t where t.userId = u.id)"
		+ " order by rm.userId")
	List<ChatPushRecipient> findRecipients(@Param("roomId") Long roomId, @Param("senderId") Long senderId);
}
