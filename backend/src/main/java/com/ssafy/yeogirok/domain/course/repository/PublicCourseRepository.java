package com.ssafy.yeogirok.domain.course.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;

/** 공개 코스 검색·메인 추천 후보(API-014·015, 명세 {@code specs/course-search-api.md} 2·{@code specs/main-api.md} 1). */
public interface PublicCourseRepository extends Repository<TravelRecord, Long> {

	/**
	 * 공개 코스에서 가져온 후보 행 조건(별칭 {@code rp}: RoomPlace, {@code target}: 대상 방). 대상 방이 삭제되지 않았고 가져간
	 * 시각({@code rp.createdAt})이 [:importedFrom, :importedTo) 안(014 는 전체 기간, 015·093 은 30일 — {@code ImportWindow}).
	 * 빼낸 후보도 센다(노션 014). 작성자가 자기 코스를 원본 기록의 방으로 가져온 후보는 세지 않는다(그 방은 이미 그 장소를 가진
	 * 방이고 인기를 스스로 올릴 수 있다 — S15P31A109-224, PR #133 리뷰). 가져간 방 수 정의를 여기 한 곳에 둔다. 시각에 null 을
	 * 넘기면 PostgreSQL 이 파라미터 타입을 정하지 못해({@code :x is null}) 기간은 늘 값으로 받는다.
	 */
	String COURSE_IMPORT_ROWS = "target.id = rp.roomId and target.deletedAt is null"
		+ " and rp.sourceType = com.ssafy.yeogirok.domain.room.entity.PlaceSourceType.COURSE"
		+ " and rp.createdAt >= :importedFrom and rp.createdAt < :importedTo"
		+ " and not exists (select 1 from TravelRecord origin where origin.id = rp.sourceRecordId"
		+ " and origin.roomId = rp.roomId)";

	/* 아래 조각은 별칭 r(TravelRecord)·room(원본 방)을 쓴다. */

	String FIRST_VISIT = "(select min(v.visitDate) from ActualVisit v where v.recordId = r.id)";
	String LAST_VISIT = "(select max(v.visitDate) from ActualVisit v where v.recordId = r.id)";
	/** 기간 안에 이 코스에서 장소를 가져간 미삭제 방 수(같은 방 1회). */
	String IMPORT_COUNT = "(select count(distinct rp.roomId) from RoomPlace rp, TravelRoom target"
		+ " where rp.sourceRecordId = r.id and " + COURSE_IMPORT_ROWS + ")";

	String CANDIDATE = "select new com.ssafy.yeogirok.domain.course.repository.CourseCandidate(r.id, r.title, room.id,"
		+ " room.startDate, room.endDate, room.transportMode, " + FIRST_VISIT + ", " + LAST_VISIT + ", " + IMPORT_COUNT + ")";

	/** 게시 중 + 원본 방 미삭제, regionCode·category(null 이면 거르지 않음). 014·015 공통. */
	String PUBLISHED_FILTERED = " from TravelRecord r, TravelRoom room"
		+ " where room.id = r.roomId and room.deletedAt is null and r.publishedAt is not null"
		+ " and (:regionPrefix is null or exists (select 1 from ActualVisit v, Place p"
		+ "   where p.id = v.placeId and v.recordId = r.id and p.regionCode like :regionPrefix))"
		+ " and (:category is null or exists (select 1 from ActualVisit v, Place p"
		+ "   where p.id = v.placeId and v.recordId = r.id and p.categoryCode = :category))";

	/** 여행 첫날·마지막 날({@code CoursePeriod} 와 같은 규칙: 방 날짜와 방문 날짜 중 이른 날·늦은 날). */
	String FIRST_DAY = "case when room.startDate is null or " + FIRST_VISIT + " < room.startDate then " + FIRST_VISIT
		+ " else room.startDate end";
	String LAST_DAY = "case when room.endDate is null or " + LAST_VISIT + " > room.endDate then " + LAST_VISIT
		+ " else room.endDate end";
	/** 공개 선택된 사진이 연결된 방문 날짜 수. */
	String PUBLIC_PHOTO_DAYS = "(select count(distinct v.visitDate) from ActualVisit v where v.recordId = r.id"
		+ " and exists (select 1 from VisitImage i where i.visitId = v.id and i.publicSelected = true))";

	/**
	 * API-014 후보. 기간·정렬·페이지는 서비스가 정한다.
	 *
	 * @param pattern      소문자 LIKE 패턴(이스케이프 문자 '!'). 제목 또는 방문 장소 이름
	 * @param regionPrefix 법정동 코드 앞자리 LIKE 패턴. 방문 장소 중 하나라도
	 */
	@Query(CANDIDATE + PUBLISHED_FILTERED
		+ " and (:transportMode is null or room.transportMode = :transportMode)"
		+ " and (:pattern is null or lower(r.title) like :pattern escape '!' or exists (select 1 from ActualVisit v, Place p"
		+ "   where p.id = v.placeId and v.recordId = r.id and lower(p.name) like :pattern escape '!'))")
	List<CourseCandidate> findCandidates(@Param("pattern") String pattern, @Param("regionPrefix") String regionPrefix,
		@Param("category") String category, @Param("transportMode") TransportMode transportMode,
		@Param("importedFrom") Instant importedFrom, @Param("importedTo") Instant importedTo);

	/**
	 * API-015 후보 전부(명세 main-api.md 2·227). 여행 첫날~마지막 날 모든 날짜에 공개 사진이 연결된 방문이 있는 코스만. 방문
	 * 날짜는 늘 기간 안이라 "공개 사진 있는 날 수 = 기간 일수"면 모든 날짜다. 고르기·순서는 서비스가 정한다({@code MainCoursePicker}).
	 */
	@Query(CANDIDATE + PUBLISHED_FILTERED
		+ " and exists (select 1 from ActualVisit v where v.recordId = r.id)"
		+ " and " + PUBLIC_PHOTO_DAYS + " = (" + LAST_DAY + " - " + FIRST_DAY + ") by day + 1"
		+ " order by r.id")
	List<CourseCandidate> findMainCourses(@Param("regionPrefix") String regionPrefix, @Param("category") String category,
		@Param("importedFrom") Instant importedFrom, @Param("importedTo") Instant importedTo);

	/** 기록별 방문 수·공개 사진 있는 방문 수·공개 사진 수(API-015 고르기). 방문이 없는 기록은 나오지 않는다. */
	@Query("select new com.ssafy.yeogirok.domain.course.repository.CoursePhotoStats(v.recordId, count(distinct v.id),"
		+ " count(distinct i.visitId), count(i.id)) from ActualVisit v"
		+ " left join VisitImage i on i.visitId = v.id and i.publicSelected = true"
		+ " where v.recordId in :recordIds group by v.recordId")
	List<CoursePhotoStats> countPhotos(@Param("recordIds") Collection<Long> recordIds);

	/** 공식 계정({@code OfficialAccount})이 만든 방의 기록 ID(API-015 사용자 코스 자리 구분). */
	@Query("select r.id from TravelRecord r, RoomMember m, User u where m.roomId = r.roomId and m.creator = true"
		+ " and u.id = m.userId and u.provider = :provider and u.providerSubject = :subject and r.id in :recordIds")
	List<Long> findOfficialIds(@Param("recordIds") Collection<Long> recordIds, @Param("provider") OAuthProvider provider,
		@Param("subject") String subject);
}
