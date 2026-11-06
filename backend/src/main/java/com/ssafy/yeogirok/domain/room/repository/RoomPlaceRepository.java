package com.ssafy.yeogirok.domain.room.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.RoomPlace;

/** 후보 목록은 담은 순서(id 오름차순)다. 응답에 장소 정보가 늘 필요해 목록·단건 조회는 places 를 같이 읽는다. */
public interface RoomPlaceRepository extends JpaRepository<RoomPlace, Long> {

	/** 뺀 후보도 찾는다. 다른 방의 후보는 찾지 않는다. */
	@EntityGraph(attributePaths = "place")
	Optional<RoomPlace> findByIdAndRoomId(Long id, Long roomId);

	@EntityGraph(attributePaths = "place")
	List<RoomPlace> findByRoomIdOrderByIdAsc(Long roomId);

	@EntityGraph(attributePaths = "place")
	List<RoomPlace> findByRoomIdAndRemovedAtIsNullOrderByIdAsc(Long roomId);

	@EntityGraph(attributePaths = "place")
	List<RoomPlace> findByRoomIdAndRemovedAtIsNotNullOrderByIdAsc(Long roomId);

	/** API-020 placeCount. 빼지 않은 후보만 센다. 후보가 없는 방은 결과에 없다. */
	@Query("select new com.ssafy.yeogirok.domain.room.repository.RoomPlaceCount(p.roomId, count(p))"
		+ " from RoomPlace p where p.roomId in :roomIds and p.removedAt is null group by p.roomId")
	List<RoomPlaceCount> countActiveByRoomIds(@Param("roomIds") Collection<Long> roomIds);
}
