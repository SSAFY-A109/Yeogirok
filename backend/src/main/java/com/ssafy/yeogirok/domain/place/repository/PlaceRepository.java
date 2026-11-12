package com.ssafy.yeogirok.domain.place.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;

public interface PlaceRepository extends JpaRepository<Place, Long> {

	/** 외부 장소 한 행(UNIQUE(provider, provider_place_id)). */
	Optional<Place> findByProviderAndProviderPlaceId(PlaceProvider provider, String providerPlaceId);
}
