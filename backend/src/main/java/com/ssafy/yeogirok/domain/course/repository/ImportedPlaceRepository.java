package com.ssafy.yeogirok.domain.course.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.RoomPlace;

/** 메인 인기 장소(API-093) 집계 대상 후보. 행 조건은 014·015 와 같은 {@link PublicCourseRepository#COURSE_IMPORT_ROWS}. */
public interface ImportedPlaceRepository extends Repository<RoomPlace, Long> {

	/** @param category 장소의 공통 카테고리. null 이면 거르지 않는다 */
	@Query("select new com.ssafy.yeogirok.domain.course.repository.ImportedPlace(rp.id, rp.roomId, rp.createdAt, p)"
		+ " from RoomPlace rp join rp.place p, TravelRoom target where " + PublicCourseRepository.COURSE_IMPORT_ROWS
		+ " and (:category is null or p.categoryCode = :category)")
	List<ImportedPlace> findImported(@Param("category") String category, @Param("importedFrom") Instant importedFrom,
		@Param("importedTo") Instant importedTo);
}
