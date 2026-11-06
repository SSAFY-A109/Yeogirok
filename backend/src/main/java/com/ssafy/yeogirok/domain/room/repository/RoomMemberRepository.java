package com.ssafy.yeogirok.domain.room.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
