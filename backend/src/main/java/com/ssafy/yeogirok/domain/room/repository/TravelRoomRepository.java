package com.ssafy.yeogirok.domain.room.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;

import jakarta.persistence.LockModeType;

public interface TravelRoomRepository extends JpaRepository<TravelRoom, Long> {

	Optional<TravelRoom> findByIdAndDeletedAtIsNull(Long id);

	/**
	 * 방 행을 잠근다(SELECT ... FOR UPDATE). 참여·재참여·나가기·초대 링크 생성을 방마다 한 줄로 세운다.
	 * 삭제된 방도 읽는다. 삭제 여부는 호출한 쪽이 잠근 뒤에 본다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from TravelRoom r where r.id = :id")
	Optional<TravelRoom> findByIdForUpdate(@Param("id") Long id);

	/**
	 * API-025. 삭제된 방도 찾는다(삭제된 방의 링크는 RO005 가 아니라 RO001).
	 *
	 * <p>엔티티가 아니라 ID 만 읽는다. 엔티티를 먼저 읽어 두면 뒤의 {@link #findByIdForUpdate} 가 잠금은 걸어도
	 * 이미 읽은 엔티티를 그대로 돌려줘, 그사이 커밋된 삭제(deleted_at)를 보지 못한다.
	 */
	@Query("select r.id from TravelRoom r where r.inviteTokenEncrypted = :inviteTokenEncrypted")
	Optional<Long> findIdByInviteTokenEncrypted(@Param("inviteTokenEncrypted") String inviteTokenEncrypted);

	/** 직접 부르지 않는다. {@code RoomVersions#increase} 만 쓴다(flush·refresh 를 함께 해야 한다). */
	@Modifying
	@Query("update TravelRoom r set r.version = r.version + 1, r.updatedAt = :now where r.id = :id")
	int increaseVersion(@Param("id") Long id, @Param("now") Instant now);

	/** API-020. 사용자가 현재 참여 중인 삭제되지 않은 방을 id 내림차순으로, {@code beforeId} 보다 작은 것만. */
	@Query("select r from TravelRoom r, RoomMember m where m.roomId = r.id and m.userId = :userId"
		+ " and m.leftAt is null and r.deletedAt is null and r.id < :beforeId order by r.id desc")
	List<TravelRoom> findJoinedRooms(@Param("userId") Long userId, @Param("beforeId") Long beforeId, Limit limit);
}
