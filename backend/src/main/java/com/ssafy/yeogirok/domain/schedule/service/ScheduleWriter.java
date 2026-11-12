package com.ssafy.yeogirok.domain.schedule.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.domain.schedule.repository.ScheduleItemRepository;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator.Timing;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * 계산을 마친 일정을 저장한다. 방 행을 잠근 트랜잭션 안에서 부른다.
 *
 * <p>{@code UNIQUE (room_id, visit_date, position)} 가 DEFERRABLE 이 아니고 Hibernate 는 한 번의 flush 에서
 * INSERT → UPDATE → DELETE 순으로 내보내므로, 지우기 → 임시 순서로 옮기기 → 최종 값 → 새 항목 넣기를 단계마다 flush 한다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleWriter {

	private final ScheduleItemRepository scheduleItemRepository;
	private final EntityManager entityManager;

	/**
	 * 항목만 저장한다. 여행방 행(일정 옵션·version)은 건드리지 않는다. 중간 flush 마다 방 변경이 따로 나가 version 이
	 * 두 번 오르지 않도록, 방 필드는 이 메서드가 끝난 뒤 부르는 쪽이 한 번에 바꾼다.
	 *
	 * <p>확정 일정을 바꾸는 경로는 모두 여기를 지난다. 부르는 쪽은 version 이 오른 뒤 그 값으로
	 * {@link com.ssafy.yeogirok.domain.room.event.RoomChange#SCHEDULE} 알림을 발행해야 한다(실시간 ROOM_UPDATED).
	 * version 을 올리는 방식(직접 올리기·dirty checking)이 경로마다 달라 여기서 대신 보내지 못한다. 지금 부르는 곳:
	 * 일정 편집(API-040), 최종 저장(API-039), 방 정보 수정(API-023, {@link ScheduleRoomChanges#apply}).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void write(Long roomId, Collection<ScheduleItem> toDelete, List<Placement> placements) {
		if (!toDelete.isEmpty()) {
			scheduleItemRepository.deleteAll(toDelete);
			entityManager.flush();
		}

		for (int i = 0; i < placements.size(); i++) {
			if (placements.get(i).existing() != null) {
				placements.get(i).existing().moveToTemporaryPosition(i);
			}
		}
		entityManager.flush();

		List<ScheduleItem> created = new ArrayList<>();
		for (Placement placement : placements) {
			ScheduleItem item = placement.existing();
			if (item == null) {
				item = ScheduleItem.create(roomId, placement.roomPlaceId(), placement.visitDate(),
					placement.position(), placement.stayMinutes(), placement.fixed());
				created.add(item);
			} else {
				item.place(placement.roomPlaceId(), placement.visitDate(), placement.position(),
					placement.stayMinutes(), placement.fixed());
			}
			Timing timing = placement.timing();
			item.applyTiming(timing.arrivalAt(), timing.departureAt(), timing.legFromPrevious());
		}
		entityManager.flush();
		scheduleItemRepository.saveAll(created);
		entityManager.flush();
	}

	/**
	 * 저장할 항목 하나.
	 *
	 * @param existing 기존 항목이면 그 행, 새 항목이면 null
	 * @param position 일차 안에서 1부터 이어지는 순서
	 */
	public record Placement(ScheduleItem existing, Long roomPlaceId, LocalDate visitDate, int position,
		int stayMinutes, boolean fixed, Timing timing) {
	}
}
