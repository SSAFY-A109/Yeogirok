package com.ssafy.yeogirok.domain.course;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.support.TestUsers;

/** 코스 공개 테스트 데이터. 회원 행이 있어야 작성자(현재 참여자 조회)에 나온다. */
public record CourseFixture(TestEntityManager em) {

	public static final LocalDate DAY1 = LocalDate.parse("2026-11-01");

	/** 회원 행(ACTIVE)을 넣고 첫 회원을 만든 사람으로 방에 넣는다. */
	public TravelRoom room(LocalDate startDate, long... members) {
		TestUsers.insertActive(em.getEntityManager(), members);
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(startDate)
			.endDate(startDate == null ? null : startDate.plusDays(2)).transportMode(TransportMode.CAR)
			.createdAt(NOW).build());
		for (int i = 0; i < members.length; i++) {
			em.persist(i == 0 ? RoomMember.creator(room.getId(), members[i], NOW)
				: RoomMember.joined(room.getId(), members[i], NOW));
		}
		return room;
	}

	public TravelRecord record(TravelRoom room) {
		return em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW));
	}

	public Long place(String name) {
		return em.persistAndGetId(Place.manual(name, new BigDecimal("37.75"), new BigDecimal("128.87"), "강원 강릉시",
			null, null, NOW), Long.class);
	}

	public ActualVisit visit(Long recordId, Long placeId, LocalDate date, int position) {
		ActualVisit visit = ActualVisit.manual(recordId, placeId, date, NOW, NOW);
		visit.moveTo(position, NOW);
		return em.persist(visit);
	}

	/** @param visitId null 이면 미연결 사진 */
	public VisitImage photo(Long recordId, Long visitId, int position) {
		VisitImage image = VisitImage.unlinked(recordId, 1L, "record-photo/" + System.nanoTime(), NOW);
		if (visitId != null) {
			image.linkTo(visitId, position);
		}
		return em.persist(image);
	}
}
