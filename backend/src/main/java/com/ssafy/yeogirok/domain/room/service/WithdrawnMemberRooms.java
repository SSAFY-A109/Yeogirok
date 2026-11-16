package com.ssafy.yeogirok.domain.room.service;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomDeletedEvent;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴 정리 중 여행방(노션 ERD › ERD 설계 관련 사항). 탈퇴 트랜잭션 안에서 돈다. 참여 행은 남긴다(탈퇴자는 조회 때 계정 상태로
 * 참여자에서 빠진다). 현재 참여자가 0명이 된 방만 나가기(API-087)처럼 소프트 삭제한다. 공개 코스가 있는 방은 남긴다
 * ({@link #deleteIfEmpty}). 남은 참여자에게 참여자 변경을 알리는
 * 일은 커밋 뒤 채팅의 {@code RoomUpdateEventListener} 가 한다(삭제된 방은 건너뛴다).
 */
@Component
@RequiredArgsConstructor
public class WithdrawnMemberRooms {

	private final TravelRoomRepository travelRoomRepository;
	private final RoomMemberRepository roomMemberRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/** 이 회원이 참여 중이던 방을 방 ID 순으로 잠그며 확인한다(교착을 피한다). */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onUserWithdrawn(UserWithdrawnEvent event) {
		// 같은 트랜잭션에서 바꾼 회원 상태(탈퇴)를 내려 보내야 아래 인원 수에서 이 회원이 빠진다.
		roomMemberRepository.flush();
		for (Long roomId : roomMemberRepository.findJoinedRoomIds(event.userId())) {
			deleteIfEmpty(roomId);
		}
	}

	/**
	 * 방 행을 잠근 뒤 센다(API-025 참여·API-087 나가기와 한 줄로 선다). 마지막 두 참여자가 동시에 탈퇴해도 뒤에 잠근 쪽이 앞
	 * 탈퇴의 커밋을 보고 0명으로 세어 방을 지운다. {@code WithdrawalCleanupJob} 도 쓴다.
	 *
	 * <p>이 방의 기록이 공개 코스로 게시 중이면 지우지 않는다. 공개 코스는 날짜·이동수단을 원본 방에서 읽고, 원본 방이 삭제되면
	 * 검색·메인·상세에서 내려간다. 탈퇴는 코스를 내리겠다는 뜻이 아니므로 방을 참여자 0명으로 남겨 코스를 살려 둔다(작성자 목록만
	 * 빈다). 이런 방의 채팅 메시지는 정리 대상에 오르지 않는다.
	 *
	 * @return 이번에 지웠는지
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean deleteIfEmpty(Long roomId) {
		TravelRoom room = travelRoomRepository.findByIdForUpdate(roomId).orElse(null);
		if (room == null || room.isDeleted() || roomMemberRepository.countCurrentMembers(roomId) > 0
			|| travelRoomRepository.hasPublishedCourse(roomId)) {
			return false;
		}
		room.softDelete(clock.instant());
		eventPublisher.publishEvent(new RoomDeletedEvent(roomId));
		return true;
	}
}
