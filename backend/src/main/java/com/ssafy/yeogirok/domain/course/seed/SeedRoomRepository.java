package com.ssafy.yeogirok.domain.course.seed;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;

/** 공식 코스 방 찾기. 방 제목 {@code tourapi-course:{contentId}:{bundleId}} 는 공개 응답에 나가지 않는다(명세 6-4·6-6). */
interface SeedRoomRepository extends Repository<TravelRoom, Long> {

	String TITLE_PREFIX = "tourapi-course:";

	@Query("select r from TravelRoom r where r.deletedAt is null and r.title like :prefix")
	List<TravelRoom> findActiveByTitlePrefix(@Param("prefix") String prefix);

	/** 내린(삭제 표시) 방까지 포함. 한 번이라도 넣은 코스는 새로 넣지 않고 다시 넣기로만 바꾼다. */
	@Query("select count(r) > 0 from TravelRoom r where r.title like :prefix")
	boolean existsEverByTitlePrefix(@Param("prefix") String prefix);

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
