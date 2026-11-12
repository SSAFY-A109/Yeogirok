package com.ssafy.yeogirok.domain.course.repository;

import java.time.Instant;

import com.ssafy.yeogirok.domain.place.entity.Place;

/** 공개 코스에서 가져온 후보 하나와 그 장소(API-093 집계 단위). */
public record ImportedPlace(Long roomPlaceId, Long roomId, Instant importedAt, Place place) {
}
