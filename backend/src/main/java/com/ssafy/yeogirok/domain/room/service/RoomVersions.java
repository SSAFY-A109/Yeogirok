package com.ssafy.yeogirok.domain.room.service;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * 방 정보는 그대로인데 방에 딸린 것(후보 장소 등)이 바뀌어 여행방 version 을 올려야 할 때 쓴다. 이 경로는 여기 하나뿐이다.
 *
 * <p>방 필드가 바뀌지 않으면 dirty checking 으로는 @Version 이 오르지 않는다. PESSIMISTIC_FORCE_INCREMENT 도
 * 이미 PESSIMISTIC_WRITE 로 잠근 엔티티에서는 올리지 않았다(11/5 확인). 그래서 잠근 행을 직접 올리고 다시 읽는다.
 * 올리기 전에 영속성 컨텍스트를 flush 하므로 방에 걸린 변경이 버려지지 않는다. 그사이 API-023 이 읽은 version 은
 * 낡게 되어 그쪽 저장은 C004 로 끝난다.
 */
@Component
@RequiredArgsConstructor
public class RoomVersions {

	private final TravelRoomRepository travelRoomRepository;
	private final EntityManager entityManager;
	private final Clock clock;

	/**
	 * {@link RoomFinder#getForMemberForUpdate} 로 행을 잠근 트랜잭션 안에서 부른다.
	 * 방 필드도 바꾼 경우에는 부르지 않는다(dirty checking 으로 이미 1 오르므로 두 번 오른다).
	 *
	 * @return 올린 뒤의 version. {@code room} 도 이 값으로 갱신된다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int increase(TravelRoom room) {
		entityManager.flush();
		travelRoomRepository.increaseVersion(room.getId(), clock.instant());
		entityManager.refresh(room);
		return room.getVersion();
	}

	/**
	 * {@link #increase} 와 같고, 확정 일정이 있으면 재계산 필요({@code schedule_needs_recalculation})도 켠다. 후보 목록을
	 * 바꾼 요청(API-029·030·031·032·094)이 부른다. 한 번의 UPDATE 로 바꿔 version 은 한 번만 오른다.
	 *
	 * @param hasSchedule 확정 일정 항목이 있는지. 없으면 다시 계산할 일정이 없어 켜지 않는다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int increaseAfterPlacesChanged(TravelRoom room, boolean hasSchedule) {
		if (!hasSchedule) {
			return increase(room);
		}
		entityManager.flush();
		travelRoomRepository.increaseVersionMarkingSchedule(room.getId(), clock.instant());
		entityManager.refresh(room);
		return room.getVersion();
	}
}
