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

	/**
	 * 탈퇴 정리용. 이 회원이 나가지 않은 삭제 전 방의 ID, 오름차순(방을 이 순서로 잠가 교착을 피한다). 회원 상태는 보지 않는다.
	 * 엔티티가 아니라 ID 만 읽는다(먼저 읽은 엔티티는 뒤에 잠가도 그사이 커밋된 삭제를 보지 못한다).
	 */
	@Query("select m.roomId from RoomMember m, TravelRoom r where r.id = m.roomId and m.userId = :userId"
		+ " and m.leftAt is null and r.deletedAt is null order by m.roomId")
	List<Long> findJoinedRoomIds(@Param("userId") Long userId);

	/**
	 * 탈퇴 정리 작업용. 현재 참여자가 없는데 삭제되지 않은 방의 ID, 오름차순. 참여자가 모두 탈퇴했는데 이 정리가 생기기 전이라
	 * 지워지지 않은 방이다. 모든 방을 훑지 않도록 탈퇴한 회원의 참여 행(드물다)에서 출발해 후보 방만 본다. 공개 코스가 있는 방은
	 * 일부러 남기는 방이라 빼서 날마다 다시 보지 않는다. 잠근 뒤 다시 센다.
	 */
	@Query("select distinct r.id from RoomMember w, User wu, TravelRoom r where wu.id = w.userId"
		+ " and wu.status = com.ssafy.yeogirok.domain.user.domain.UserStatus.WITHDRAWN and w.leftAt is null"
		+ " and r.id = w.roomId and r.deletedAt is null and not exists"
		+ " (select 1 from RoomMember m, User u where m.roomId = r.id and" + CURRENT_MEMBER + ")"
		+ " and not exists (select 1 from TravelRecord c where c.roomId = r.id and c.publishedAt is not null)"
		+ " order by r.id")
	List<Long> findEmptyRoomIds();

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
