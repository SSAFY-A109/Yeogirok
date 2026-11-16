package com.ssafy.yeogirok.domain.user.cleanup;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.footprint.repository.FootprintRepository;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.service.WithdrawnMemberRooms;
import com.ssafy.yeogirok.domain.statistics.WithdrawnFootprints;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴 데이터 정리가 생기기 전에 탈퇴한 회원의 몫을 마저 정리한다. 탈퇴 처리(API-009)가 트랜잭션 안에서 하는 일과 같다.
 * <ul>
 *   <li>탈퇴했는데 발자취가 남은 회원: 회원 행을 잠그고 발자취를 보존분으로 옮긴 뒤 지운다.</li>
 *   <li>현재 참여자가 없는데 삭제되지 않은 방: 방 행을 잠그고 다시 센 뒤 지운다(커밋 뒤 채팅이 메시지를 정리한다).</li>
 * </ul>
 * 대상마다 따로 커밋한다. 하나가 실패해도 나머지는 계속하고, 실패한 것은 다음 주기에 다시 잡힌다. 서버 여러 대가 같이 돌아도
 * 잠근 뒤 다시 확인하므로 두 번 정리하지 않는다. 정리할 게 없으면 조회 두 번으로 끝난다.
 *
 * <p>새 탈퇴는 탈퇴 트랜잭션에서 정리하므로 배포 뒤 첫 실행 이후로는 놓친 게 없는지 보는 안전망이다. 그래서 하루에 한 번
 * ({@code auth.withdrawal.cleanup-interval}) 돈다. 첫 실행은 서버가 뜬 뒤 짧게({@code cleanup-initial-delay}) 기다렸다
 * 돈다. 배포가 잦아 서버가 하루를 못 넘겨도 배포마다 한 번은 돈다.
 */
@Slf4j
@Component
public class WithdrawalCleanupJob {

	private final FootprintRepository footprintRepository;
	private final RoomMemberRepository roomMemberRepository;
	private final UserRepository userRepository;
	private final WithdrawnFootprints withdrawnFootprints;
	private final WithdrawnMemberRooms withdrawnMemberRooms;
	private final TransactionTemplate transactionTemplate;

	public WithdrawalCleanupJob(FootprintRepository footprintRepository, RoomMemberRepository roomMemberRepository,
		UserRepository userRepository, WithdrawnFootprints withdrawnFootprints,
		WithdrawnMemberRooms withdrawnMemberRooms, PlatformTransactionManager transactionManager) {
		this.footprintRepository = footprintRepository;
		this.roomMemberRepository = roomMemberRepository;
		this.userRepository = userRepository;
		this.withdrawnFootprints = withdrawnFootprints;
		this.withdrawnMemberRooms = withdrawnMemberRooms;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Scheduled(fixedDelayString = "${auth.withdrawal.cleanup-interval}",
		initialDelayString = "${auth.withdrawal.cleanup-initial-delay}")
	public void cleanUp() {
		for (Long userId : footprintRepository.findWithdrawnUserIds()) {
			try {
				transactionTemplate.executeWithoutResult(status -> cleanUpFootprints(userId));
			} catch (RuntimeException e) {
				log.warn("탈퇴한 회원의 발자취 정리에 실패해 다음 주기에 다시 시도합니다. userId={}", userId, e);
			}
		}
		for (Long roomId : roomMemberRepository.findEmptyRoomIds()) {
			try {
				if (Boolean.TRUE.equals(transactionTemplate.execute(status -> withdrawnMemberRooms.deleteIfEmpty(roomId)))) {
					log.info("참여자가 모두 탈퇴한 방을 지웠습니다. roomId={}", roomId);
				}
			} catch (RuntimeException e) {
				log.warn("참여자가 없는 방 정리에 실패해 다음 주기에 다시 시도합니다. roomId={}", roomId, e);
			}
		}
	}

	/** 탈퇴 처리와 같은 회원 행 잠금 아래에서 한다(그사이 다시 활성일 수는 없지만 잠근 뒤 상태를 다시 본다). */
	private void cleanUpFootprints(Long userId) {
		userRepository.findByIdForUpdate(userId)
			.filter(user -> !user.isActive())
			.map(User::getId)
			.ifPresent(withdrawnFootprints::preserveAndDelete);
	}
}
