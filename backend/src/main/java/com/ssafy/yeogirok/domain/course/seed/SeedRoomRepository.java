package com.ssafy.yeogirok.domain.course.seed;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;

/**
 * 공식 코스 방 찾기. 방 제목 {@code tourapi-course:{contentId}:{bundleId}} 는 공개 응답에 나가지 않는다(명세 6-4·6-6).
 * 회원도 같은 제목으로 방을 만들 수 있어 적재 주인이 만든 방만 본다(PR #130 리뷰).
 */
interface SeedRoomRepository extends Repository<TravelRoom, Long> {

	String TITLE_PREFIX = "tourapi-course:";
	String OWNED = " and exists (select 1 from RoomMember m where m.roomId = r.id and m.userId = :ownerId"
		+ " and m.creator = true)";

	@Query("select r from TravelRoom r where r.deletedAt is null and r.title like :prefix" + OWNED)
	List<TravelRoom> findActiveByTitlePrefix(@Param("prefix") String prefix, @Param("ownerId") Long ownerId);

	/** 내린(삭제 표시) 방까지 포함. 한 번이라도 넣은 코스는 새로 넣지 않고 다시 넣기로만 바꾼다. */
	@Query("select count(r) > 0 from TravelRoom r where r.title like :prefix" + OWNED)
	boolean existsEverByTitlePrefix(@Param("prefix") String prefix, @Param("ownerId") Long ownerId);

	static String titleOf(String contentId, String bundleId) {
		return TITLE_PREFIX + contentId + ":" + bundleId;
	}

	/** 그 코스(아무 묶음)의 방 제목 앞부분 + like 와일드카드. */
	static String coursePattern(String contentId) {
		return TITLE_PREFIX + contentId + ":%";
	}

	static String allPattern() {
		return TITLE_PREFIX + "%";
	}
}
