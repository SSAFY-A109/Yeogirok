package com.ssafy.yeogirok.domain.course.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;

/**
 * 메인 인기 장소(API-093) 집계 대상 후보. 행 조건은 014·015 와 같은 {@link PublicCourseRepository#COURSE_IMPORT_ROWS} 에 더해,
 * 가져온 원본 코스가 지금 게시 중이고 원본 방이 삭제되지 않았을 것(공개되지 않은 코스를 근거로 추천하지 않는다, PR #107 리뷰).
 * 014·015 는 게시 중인 코스만 다뤄 같은 조건이 이미 걸려 있다.
 */
public interface ImportedPlaceRepository extends Repository<RoomPlace, Long> {

	/** @param category 장소의 공통 카테고리. null 이면 거르지 않는다 */
	@Query("select new com.ssafy.yeogirok.domain.course.repository.ImportedPlace(rp.id, rp.roomId, rp.createdAt, p)"
		+ " from RoomPlace rp join rp.place p, TravelRoom target, TravelRecord source, TravelRoom sourceRoom"
		+ " where " + PublicCourseRepository.COURSE_IMPORT_ROWS
		+ " and source.id = rp.sourceRecordId and source.publishedAt is not null"
		+ " and sourceRoom.id = source.roomId and sourceRoom.deletedAt is null"
		+ " and (:category is null or p.categoryCode = :category)")
	List<ImportedPlace> findImported(@Param("category") String category, @Param("importedFrom") Instant importedFrom,
		@Param("importedTo") Instant importedTo);

	/**
	 * 공식 코스(공식 계정 또는 적재 주인으로 설정한 회원이 만든 방, 게시 중, 방 미삭제)의 방문 장소. 공식 코스 방을 "그 장소를 담은
	 * 여행방 1곳"으로 센다(S15P31A109-231). 30일 기간은 보지 않는다. roomPlaceId 자리에는 방문 ID, importedAt 에는 게시 시각을 넣는다.
	 */
	@Query("select new com.ssafy.yeogirok.domain.course.repository.ImportedPlace(v.id, room.id, r.publishedAt, p)"
		+ " from ActualVisit v, Place p, TravelRecord r, TravelRoom room, RoomMember m, User u"
		+ " where p.id = v.placeId and r.id = v.recordId and r.publishedAt is not null"
		+ " and room.id = r.roomId and room.deletedAt is null"
		+ " and m.roomId = room.id and m.creator = true and u.id = m.userId"
		+ " and ((u.provider = :provider and u.providerSubject = :subject) or u.id = :ownerUserId)"
		+ " and (:category is null or p.categoryCode = :category)")
	List<ImportedPlace> findOfficialCoursePlaces(@Param("category") String category,
		@Param("provider") OAuthProvider provider, @Param("subject") String subject,
		@Param("ownerUserId") Long ownerUserId);
}
