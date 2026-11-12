package com.ssafy.yeogirok.domain.record.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.place.entity.Place;

/** 직접 입력 장소를 지워도 되는지 확인하는 조회(방문·여행방 후보가 가리키는지). 장소 하나 조회는 {@code PlaceRepository}. */
public interface RecordPlaceRepository extends Repository<Place, Long> {

	/** 이 장소를 가리키는 실제 방문이 있는지. FK 가 없어 지우기 전에 서비스에서 확인한다. */
	@Query("select count(v) > 0 from ActualVisit v where v.placeId = :placeId")
	boolean isUsedByVisit(@Param("placeId") Long placeId);

	/** 이 장소를 가리키는 여행방 후보가 있는지(뺀 후보 포함). */
	@Query("select count(r) > 0 from RoomPlace r where r.place.id = :placeId")
	boolean isUsedByRoomPlace(@Param("placeId") Long placeId);
}
