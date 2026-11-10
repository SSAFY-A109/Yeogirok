package com.ssafy.yeogirok.domain.chat.push;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.chat.dto.ChatMessage;
import com.ssafy.yeogirok.domain.push.repository.ChatPushTarget;
import com.ssafy.yeogirok.domain.push.repository.UserPushTokenRepository;
import com.ssafy.yeogirok.domain.push.sender.PushMessage;
import com.ssafy.yeogirok.domain.push.sender.PushResult;
import com.ssafy.yeogirok.domain.push.sender.PushSender;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 새 채팅 메시지 앱 푸시(노션 API-077 "채팅 푸시 알림 (Android)").
 *
 * <ol>
 *   <li>WebSocket 처리 스레드는 줄에 넣고 바로 돌아온다. 발송은 전용 스레드가 한다. 줄이 넘치거나 발송이 실패해도
 *       메시지 저장·ACK·방송에는 영향이 없다.</li>
 *   <li>받는 사람: 방이 살아 있고 현재 참여 중이며 계정이 활성인 회원 중 보낸 사람을 뺀, 토큰이 있는 회원.</li>
 *   <li>그 방에 READY 로 연결된 회원은 모든 기기에서 뺀다({@link ChatPresence}).</li>
 *   <li>남은 회원마다 발송 직전에 3초 제한을 확인하고({@link ChatPushThrottle}) 등록된 기기 모두에 보낸다.</li>
 *   <li>FCM 이 UNREGISTERED 를 돌려준 토큰은 보낼 당시 주인과 같을 때만 지운다(그사이 다른 계정으로 옮겨 갔으면 둔다).</li>
 * </ol>
 *
 * <p>실행기는 빈으로 등록하지 않는다. {@code Executor} 빈이 생기면 스프링 부트 기본 실행기(@Async)가 물러난다.
 */
@Slf4j
@Service
public class ChatPushService {

	/** 종료할 때 줄에 남은 푸시를 기다리는 최대 시간. FCM 응답 제한(10초)보다 짧게 둔다. */
	private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

	private final UserPushTokenRepository tokenRepository;
	private final ChatPresence presence;
	private final ChatPushThrottle throttle;
	private final PushSender sender;
	private final ChatPushProperties properties;
	private final ThreadPoolExecutor executor;

	public ChatPushService(UserPushTokenRepository tokenRepository, ChatPresence presence, ChatPushThrottle throttle,
		PushSender sender, ChatPushProperties properties) {
		this.tokenRepository = tokenRepository;
		this.presence = presence;
		this.throttle = throttle;
		this.sender = sender;
		this.properties = properties;
		this.executor = new ThreadPoolExecutor(properties.threads(), properties.threads(), 0, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(properties.queueCapacity()), new CustomizableThreadFactory("chat-push-"),
			new ThreadPoolExecutor.AbortPolicy());
	}

	/**
	 * 종료. 이미 줄에 든 푸시는 잠깐 기다려 마저 보낸다. 이 빈이 발송기·DB 보다 먼저 정리되므로(의존 순서) 기다리는 동안은
	 * 둘 다 살아 있다. 시간 안에 못 끝낸 것은 버린다.
	 */
	@PreDestroy
	void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
				int dropped = executor.shutdownNow().size();
				log.warn("종료 대기 시간 안에 채팅 푸시를 다 보내지 못해 남은 {}건을 버립니다.", dropped);
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	/** 커밋된 새 메시지(replayed=false)에서만 부른다. 예외를 던지지 않는다. */
	public void notifyNewMessage(Long roomId, ChatMessage message) {
		ChatPushJob job = new ChatPushJob(roomId, message.sender().id(), message.sender().nickname(), message.id(),
			message.body());
		try {
			executor.execute(() -> deliver(job));
		} catch (RejectedExecutionException e) {
			log.warn("채팅 푸시 대기 줄이 가득 차 이 메시지의 푸시를 보내지 않습니다. roomId={}, messageId={}", roomId,
				message.id());
		}
	}

	/** 발송 스레드에서 돈다. 테스트는 직접 불러 결과를 바로 본다. */
	void deliver(ChatPushJob job) {
		try {
			List<ChatPushTarget> targets = tokenRepository.findChatPushTargets(job.roomId(), job.senderId());
			if (targets.isEmpty()) {
				return;
			}
			Set<Long> online = presence.onlineUsers(job.roomId());
			Map<Long, List<ChatPushTarget>> byUser = new LinkedHashMap<>();
			for (ChatPushTarget target : targets) {
				if (!online.contains(target.userId())) {
					byUser.computeIfAbsent(target.userId(), userId -> new ArrayList<>()).add(target);
				}
			}
			PushMessage message = ChatPushMessages.of(job.roomId(), job.messageId(), targets.getFirst().roomTitle(),
				job.senderNickname(), job.body(), properties.previewLength());
			byUser.forEach((userId, devices) -> sendTo(job, userId, devices, message));
		} catch (RuntimeException e) {
			log.error("채팅 푸시를 보내지 못했습니다. roomId={}, messageId={}", job.roomId(), job.messageId(), e);
		}
	}

	private void sendTo(ChatPushJob job, Long userId, List<ChatPushTarget> devices, PushMessage message) {
		if (!throttle.tryAcquire(userId, job.roomId())) {
			return;
		}
		List<String> tokens = devices.stream().map(ChatPushTarget::token).toList();
		List<PushResult> results = sender.send(tokens, message);
		if (results.stream().noneMatch(result -> result == PushResult.SENT)) {
			// 한 기기에도 가지 않았다(FCM 장애, 모두 해제된 토큰 등). 제한을 풀어 다음 메시지에서 다시 보낸다.
			throttle.release(userId, job.roomId());
		}
		for (int i = 0; i < tokens.size() && i < results.size(); i++) {
			if (results.get(i) == PushResult.UNREGISTERED) {
				tokenRepository.deleteByUserIdAndToken(userId, tokens.get(i));
				log.info("FCM 이 등록 해제된 토큰이라 알려 지웠습니다. userId={}", userId);
			}
		}
	}

	/** 발송 스레드로 넘기는 값. 엔티티·연결을 넘기지 않는다. */
	record ChatPushJob(Long roomId, Long senderId, String senderNickname, Long messageId, String body) {

		@Override
		public String toString() {
			return "ChatPushJob[roomId=" + roomId + ", messageId=" + messageId + "]";
		}
	}
}
