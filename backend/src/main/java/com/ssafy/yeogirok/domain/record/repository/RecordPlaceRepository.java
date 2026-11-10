package com.ssafy.yeogirok.domain.record.repository;

import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;

/** 실제 방문에 쓸 저장된 장소 찾기. 장소 코드(PlaceRepository)를 바꾸지 않으려고 따로 둔다. */
public interface TourPlaceRepository extends Repository<Place, Long> {

	Optional<Place> findByProviderAndProviderPlaceId(PlaceProvider provider, String providerPlaceId);
}
