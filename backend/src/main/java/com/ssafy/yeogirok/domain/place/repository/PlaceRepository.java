package com.ssafy.yeogirok.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.yeogirok.domain.place.entity.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
