package com.ssafy.yeogirok.domain.record.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;

import jakarta.persistence.LockModeType;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long> {

	boolean existsByRoomId(Long roomId);

	@Query("select r.roomId from TravelRecord r where r.id = :id")
	Optional<Long> findRoomIdById(@Param("id") Long id);

	/** 기록 쓰기를 기록마다 한 줄로 세운다(SELECT ... FOR UPDATE). 방 행은 함께 잠그지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from TravelRecord r where r.id = :id")
	Optional<TravelRecord> findByIdForUpdate(@Param("id") Long id);
}
