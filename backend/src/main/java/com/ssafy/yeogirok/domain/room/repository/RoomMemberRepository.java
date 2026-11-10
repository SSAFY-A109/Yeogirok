package com.ssafy.yeogirok.domain.room.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;

/**
 * 현재 참여자는 {@code left_at IS NULL} 이고 회원이 탈퇴하지 않은 행이다. 탈퇴자는 참여자 목록·인원에서 뺀다
 * (노션 확정 정책 "탈퇴자는 현재 참여자에서 제외").
 */
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

	String CURRENT_MEMBER = " m.leftAt is null and u.id = m.userId"
		+ " and u.status = com.ssafy.yeogirok.domain.user.domain.UserStatus.ACTIVE";

	@Query("select count(m) from RoomMember m, User u where m.roomId = :roomId and" + CURRENT_MEMBER)
	long countCurrentMembers(@Param("roomId") Long roomId);

	/** 방마다 들어온 순서(같으면 회원 id 순). */
	@Query("select new com.ssafy.yeogirok.domain.room.repository.RoomMemberView("
		+ "m.roomId, m.userId, u.nickname, u.profileImageUrl, m.creator, m.lastReadMessageId)"
		+ " from RoomMember m, User u where m.roomId in :roomIds and" + CURRENT_MEMBER
		+ " order by m.roomId, m.joinedAt, m.userId")
	List<RoomMemberView> findCurrentMembers(@Param("roomIds") Collection<Long> roomIds);

	/**
	 * API-088. 현재 참여 중인 본인 행의 읽은 위치를 앞으로만 옮긴다(GREATEST). 방 행을 잠그지 않고 참여 행 하나만 바꾸는
	 * 조건부 UPDATE 라 일정 저장·후보 변경·메시지 전송을 기다리지 않는다. 나간 뒤면 바뀌는 행이 없다.
	 *
	 * @return 바뀐 행 수. 같거나 작은 값이면 0
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RoomMember m set m.lastReadMessageId = :messageId"
		+ " where m.roomId = :roomId and m.userId = :userId and m.leftAt is null"
		+ " and (m.lastReadMessageId is null or m.lastReadMessageId < :messageId)")
	int advanceLastRead(@Param("roomId") Long roomId, @Param("userId") Long userId,
		@Param("messageId") Long messageId);

	/** 현재 참여 중인 본인 행의 읽은 위치. 나갔거나 행이 없으면 비어 있다. */
	@Query("select m from RoomMember m where m.roomId = :roomId and m.userId = :userId and m.leftAt is null")
	Optional<RoomMember> findCurrent(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
