package com.ssafy.yeogirok.domain.course.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/** 공개 코스 검색·메인 추천 후보(API-014·015, 명세 {@code specs/course-search-api.md} 2·{@code specs/main-api.md} 1). */
public interface PublicCourseRepository extends Repository<TravelRecord, Long> {

	/**
	 * 공개 코스에서 가져온 후보 행 조건(별칭 {@code rp}: RoomPlace, {@code target}: 대상 방). 대상 방이 삭제되지 않았고 가져간
	 * 시각({@code rp.createdAt})이 [:importedFrom, :importedTo) 안(014 는 전체 기간, 015·093 은 30일 — {@code ImportWindow}).
	 * 빼낸 후보도 센다(노션 014). 가져간 방 수 정의를 여기 한 곳에 둔다. 시각에 null 을 넘기면 PostgreSQL 이 파라미터 타입을
	 * 정하지 못해({@code :x is null}) 기간은 늘 값으로 받는다.
	 */
	String COURSE_IMPORT_ROWS = "target.id = rp.roomId and target.deletedAt is null"
		+ " and rp.sourceType = com.ssafy.yeogirok.domain.room.entity.PlaceSourceType.COURSE"
		+ " and rp.createdAt >= :importedFrom and rp.createdAt < :importedTo";

	/**
	 * 게시 중 + 원본 방 미삭제. 조건이 null 이면 거르지 않는다. importCount 는 기간 안에 이 코스에서 장소를 가져간 미삭제 방 수
	 * (같은 방 1회).
	 *
	 * @param pattern      소문자 LIKE 패턴(이스케이프 문자 '!'). 제목 또는 방문 장소 이름
	 * @param regionPrefix 법정동 코드 앞자리 LIKE 패턴. 방문 장소 중 하나라도
	 */
	@Query("select new com.ssafy.yeogirok.domain.course.repository.CourseCandidate(r.id, r.title, room.id,"
		+ " room.startDate, room.endDate, room.transportMode,"
		+ " (select min(v.visitDate) from ActualVisit v where v.recordId = r.id),"
		+ " (select max(v.visitDate) from ActualVisit v where v.recordId = r.id),"
		+ " (select count(distinct rp.roomId) from RoomPlace rp, TravelRoom target where rp.sourceRecordId = r.id and "
		+ COURSE_IMPORT_ROWS + "))"
		+ " from TravelRecord r, TravelRoom room"
		+ " where room.id = r.roomId and room.deletedAt is null and r.publishedAt is not null"
		+ " and (:transportMode is null or room.transportMode = :transportMode)"
		+ " and (:pattern is null or lower(r.title) like :pattern escape '!' or exists (select 1 from ActualVisit v, Place p"
		+ "   where p.id = v.placeId and v.recordId = r.id and lower(p.name) like :pattern escape '!'))"
		+ " and (:regionPrefix is null or exists (select 1 from ActualVisit v, Place p"
		+ "   where p.id = v.placeId and v.recordId = r.id and p.regionCode like :regionPrefix))"
		+ " and (:category is null or exists (select 1 from ActualVisit v, Place p"
		+ "   where p.id = v.placeId and v.recordId = r.id and p.categoryCode = :category))")
	List<CourseCandidate> findCandidates(@Param("pattern") String pattern, @Param("regionPrefix") String regionPrefix,
		@Param("category") String category, @Param("transportMode") TransportMode transportMode,
		@Param("importedFrom") Instant importedFrom, @Param("importedTo") Instant importedTo);
}
