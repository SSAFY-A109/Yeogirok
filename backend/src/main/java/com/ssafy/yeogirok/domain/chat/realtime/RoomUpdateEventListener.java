package com.ssafy.yeogirok.domain.chat.realtime;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomChange;
import com.ssafy.yeogirok.domain.room.event.RoomChangedEvent;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 여행방 변경이 커밋된 직후 그 방 채널({@code chat:room:{roomId}})로 {@code ROOM_UPDATED} 를 보낸다. 채팅 메시지와 같은 연결·
 * 같은 구독을 쓰므로 그 방에 READY 로 연결된 모든 참여자(바꾼 사람 포함)가 받는다.
 *
 * <p>요청 스레드는 줄에 넣고 바로 돌아온다(탈퇴만 참여 방 조회 한 번을 먼저 한다). Redis 발행은 전용 스레드 하나가
 * 넣은 순서대로 한다. Redis 가 느리거나 멈춰도 이미 커밋된 요청의 응답이 늦어지지 않는다. 줄이 가득 차면 그 알림은 버린다.
 *
 * <p>알림일 뿐 전달을 보장하지 않는다. 실패하면 로그만 남기고, 앱은 다시 연결하거나 화면에 돌아올 때 새로 받아 맞춘다.
 * 실행기는 빈으로 등록하지 않는다({@code Executor} 빈이 생기면 스프링 부트 기본 실행기가 물러난다).
 *
 * <p>TODO Redis 명령 제한 시간: {@code spring.data.redis.timeout} 이 없어 Lettuce 기본값(60초)이 걸린다. Redis 가
 * 응답 없이 멈추면 발행 스레드가 알림마다 최대 60초씩 막혀 줄이 밀리고, 풀린 뒤 밀린 알림이 한꺼번에 나간다. 2~3초로
 * 줄이는 것을 검토한다. 채팅 티켓·멱등키·접속 상태 등 Redis 를 쓰는 모든 기능에 함께 걸리므로 영향을 같이 보고 정한다.
 */
@Slf4j
@Component
public class RoomUpdateEventListener {

	/** 발행을 기다리는 알림 한도. Redis 가 멈춘 동안 메모리가 늘지 않게 한다. */
	static final int QUEUE_CAPACITY = 1_000;
	/** 종료할 때 줄에 남은 알림을 기다리는 최대 시간. */
	private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(3);

	private final ChatPublisher publisher;
	private final TravelRoomRepository travelRoomRepository;
	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
		new ArrayBlockingQueue<>(QUEUE_CAPACITY), new CustomizableThreadFactory("room-update-"),
		new ThreadPoolExecutor.AbortPolicy());

	public RoomUpdateEventListener(ChatPublisher publisher, TravelRoomRepository travelRoomRepository) {
		this.publisher = publisher;
		this.travelRoomRepository = travelRoomRepository;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
				log.warn("종료 대기 시간 안에 여행방 변경 알림을 다 보내지 못했습니다. 대기 중인 {}건은 버립니다.",
					executor.shutdownNow().size());
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRoomChanged(RoomChangedEvent event) {
		try {
			executor.execute(() -> publish(event));
		} catch (RejectedExecutionException e) {
			log.warn("여행방 변경 알림 대기 줄이 가득 찼거나 종료 중이라 보내지 않습니다. roomId={}, version={}, changes={}",
				event.roomId(), event.version(), event.changes());
		}
	}

	/**
	 * 탈퇴한 회원은 참여 행이 남아 있어도 참여자 목록에서 빠진다(조회 때 계정 상태로 거른다). 그 회원이 참여 중이던 방마다
	 * {@link RoomChange#MEMBERS} 를 알린다. 방 조회는 커밋 뒤 요청 스레드에서 한 번 한다(탈퇴는 드물고 조회 한 번이라
	 * 응답이 크게 늦지 않는다). 발행 스레드에서 하면 DB 가 느린 동안 다른 방 알림까지 막힌다. 조회가 실패해도 탈퇴는 이미
	 * 커밋돼 그대로 성공한다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onUserWithdrawn(UserWithdrawnEvent event) {
		publishWithdrawal(event.userId());
	}

	private void publishWithdrawal(Long userId) {
		List<TravelRoom> rooms;
		try {
			rooms = travelRoomRepository.findJoinedRooms(userId, Long.MAX_VALUE, Limit.unlimited());
		} catch (RuntimeException e) {
			log.warn("탈퇴한 회원의 참여 방을 읽지 못해 알림을 보내지 않습니다. userId={}", userId, e);
			return;
		}
		for (TravelRoom room : rooms) {
			onRoomChanged(RoomChangedEvent.of(room.getId(), userId, room.getVersion(), RoomChange.MEMBERS));
		}
	}

	private void publish(RoomChangedEvent event) {
		try {
			publisher.publishMessage(event.roomId(), ChatFrames.RoomUpdated.from(event));
		} catch (RuntimeException e) {
			log.warn("여행방 변경 알림을 보내지 못했습니다. roomId={}, version={}, changes={}", event.roomId(),
				event.version(), event.changes(), e);
		}
	}
}
