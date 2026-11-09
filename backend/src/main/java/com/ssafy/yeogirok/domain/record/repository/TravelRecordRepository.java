package com.ssafy.yeogirok.domain.record.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;

import jakarta.persistence.LockModeType;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long> {

	boolean existsByRoomId(Long roomId);

	/**
	 * API-058. 사용자가 현재 참여 중이고 방이 삭제되지 않은 기록을 id 내림차순으로, {@code beforeId} 보다 작은 것만.
	 * roomId·status 가 null 이면 거르지 않는다.
	 */
	@Query("select r from TravelRecord r, TravelRoom room, RoomMember m"
		+ " where room.id = r.roomId and m.roomId = r.roomId and m.userId = :userId and m.leftAt is null"
		+ " and room.deletedAt is null and (:roomId is null or r.roomId = :roomId)"
		+ " and (:status is null or r.status = :status) and r.id < :beforeId order by r.id desc")
	List<TravelRecord> findJoinedRecords(@Param("userId") Long userId, @Param("roomId") Long roomId,
		@Param("status") RecordStatus status, @Param("beforeId") Long beforeId, Limit limit);

	@Query("select r.roomId from TravelRecord r where r.id = :id")
	Optional<Long> findRoomIdById(@Param("id") Long id);

	/** 기록 쓰기를 기록마다 한 줄로 세운다(SELECT ... FOR UPDATE). 방 행은 함께 잠그지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from TravelRecord r where r.id = :id")
	Optional<TravelRecord> findByIdForUpdate(@Param("id") Long id);
}
