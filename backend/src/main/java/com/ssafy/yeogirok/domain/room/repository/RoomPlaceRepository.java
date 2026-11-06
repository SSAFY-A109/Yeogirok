package com.ssafy.yeogirok.domain.room.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.RoomPlace;

/** 후보 목록은 담은 순서(id 오름차순)다. */
public interface RoomPlaceRepository extends JpaRepository<RoomPlace, Long> {

	/** 뺀 후보도 찾는다. 다른 방의 후보는 찾지 않는다. */
	Optional<RoomPlace> findByIdAndRoomId(Long id, Long roomId);

	List<RoomPlace> findByRoomIdOrderByIdAsc(Long roomId);

	List<RoomPlace> findByRoomIdAndRemovedAtIsNullOrderByIdAsc(Long roomId);

	List<RoomPlace> findByRoomIdAndRemovedAtIsNotNullOrderByIdAsc(Long roomId);

	/** API-020 placeCount. 빼지 않은 후보만 센다. 후보가 없는 방은 결과에 없다. */
	@Query("select new com.ssafy.yeogirok.domain.room.repository.RoomPlaceCount(p.roomId, count(p))"
		+ " from RoomPlace p where p.roomId in :roomIds and p.removedAt is null group by p.roomId")
	List<RoomPlaceCount> countActiveByRoomIds(@Param("roomIds") Collection<Long> roomIds);
}
