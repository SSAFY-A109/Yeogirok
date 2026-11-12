package com.ssafy.yeogirok.domain.room.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
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

	/**
	 * API-034 인기 점수. 외부 장소별로 {@code since} 이후 이 장소를 담은 <b>다른</b> 여행방 수(같은 방은 1회).
	 * 추천을 요청한 방({@code excludedRoomId})과 삭제된 방은 빼고, 담은 뒤 뺀 후보도 센다(담았다는 사실).
	 * 담은 방이 없는 장소는 결과에 없다.
	 */
	@Query("select new com.ssafy.yeogirok.domain.room.repository.PlacePopularity(p.providerPlaceId, count(distinct rp.roomId))"
		+ " from RoomPlace rp join rp.place p"
		+ " where p.provider = :provider and p.providerPlaceId in :providerPlaceIds and rp.createdAt >= :since"
		+ " and rp.roomId <> :excludedRoomId"
		+ " and exists (select 1 from TravelRoom r where r.id = rp.roomId and r.deletedAt is null)"
		+ " group by p.providerPlaceId")
	List<PlacePopularity> countOtherRoomsByProviderPlaceIds(@Param("provider") PlaceProvider provider,
		@Param("providerPlaceIds") Collection<String> providerPlaceIds, @Param("since") Instant since,
		@Param("excludedRoomId") Long excludedRoomId);
}
