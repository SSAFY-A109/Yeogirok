package com.ssafy.yeogirok.domain.record.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long> {

	@Query("select r.roomId from TravelRecord r where r.id = :id")
	Optional<Long> findRoomIdById(@Param("id") Long id);
}
