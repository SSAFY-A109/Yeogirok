package com.ssafy.yeogirok.domain.push.sender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Firebase Admin SDK 로 FCM HTTP v1 에 보낸다. 메시지 모양은 노션 API-077 "FCM HTTP v1 요청의 message 예시"를 따른다.
 *
 * <p>토큰·키·본문은 로그에 남기지 않는다.
 */
@Slf4j
public class FcmPushSender implements PushSender {

	/** FCM sendEach 한 번에 보낼 수 있는 최대 메시지 수. */
	private static final int MAX_BATCH = 500;
	private static final String APP_NAME = "yeogirok-push";
	private static final AtomicInteger APP_SEQUENCE = new AtomicInteger();

	private final FirebaseApp app;
	private final FirebaseMessaging messaging;

	private FcmPushSender(FirebaseApp app) {
		this.app = app;
		this.messaging = FirebaseMessaging.getInstance(app);
	}

	/** 키를 읽지 못하면 시작을 멈춘다(값을 잘못 넣은 것이므로). 오류 메시지에 키 내용을 담지 않는다. */
	public static FcmPushSender create(FcmProperties properties) {
		byte[] json;
		try {
			json = Base64.getMimeDecoder().decode(properties.serviceAccountBase64().strip());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("FIREBASE_SERVICE_ACCOUNT_BASE64 를 Base64 로 읽지 못했습니다. 서비스 계정 키 JSON 파일 전체를"
				+ " Base64 한 줄로 넣어야 합니다.");
		}
		ServiceAccountCredentials credentials;
		try {
			credentials = ServiceAccountCredentials.fromStream(new ByteArrayInputStream(json));
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException("FIREBASE_SERVICE_ACCOUNT_BASE64 가 Firebase 서비스 계정 키 JSON 이 아닙니다.");
		}
		FirebaseOptions options = FirebaseOptions.builder()
			.setCredentials(credentials)
			.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
			.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
			.build();
		// 이름이 겹치면 initializeApp 이 실패한다. 한 JVM 에 컨텍스트가 둘 떠도(테스트 컨텍스트 캐시 등) 겹치지 않게 번호를 붙인다.
		String name = APP_NAME + "-" + APP_SEQUENCE.incrementAndGet();
		FcmPushSender sender = new FcmPushSender(FirebaseApp.initializeApp(options, name));
		log.info("FCM 채팅 푸시를 켰습니다. projectId={}", sender.app.getOptions().getProjectId());
		return sender;
	}

	@Override
	public List<PushResult> send(List<String> tokens, PushMessage message) {
		List<PushResult> results = new ArrayList<>(tokens.size());
		for (int from = 0; from < tokens.size(); from += MAX_BATCH) {
			List<String> batch = tokens.subList(from, Math.min(from + MAX_BATCH, tokens.size()));
			if (Thread.currentThread().isInterrupted()) {
				// 종료 중이다. 남은 토큰은 보내지 않는다.
				results.addAll(Collections.nCopies(tokens.size() - from, PushResult.UNAVAILABLE));
				break;
			}
			results.addAll(sendBatch(batch, message));
		}
		return results;
	}

	private List<PushResult> sendBatch(List<String> tokens, PushMessage message) {
		try {
			BatchResponse response = messaging.sendEach(tokens.stream().map(token -> toMessage(token, message)).toList());
			return response.getResponses().stream().map(FcmPushSender::toResult).toList();
		} catch (FirebaseMessagingException | RuntimeException e) {
			if (causedByInterrupt(e)) {
				// SDK 가 인터럽트를 감싸 던지면 상태가 지워질 수 있다. 다시 세워 부르는 쪽이 멈추게 한다.
				Thread.currentThread().interrupt();
				log.info("종료 중이라 FCM 발송을 멈춥니다. tokens={}", tokens.size());
			} else {
				log.warn("FCM 발송에 실패했습니다. tokens={}", tokens.size(), e);
			}
			return Collections.nCopies(tokens.size(), PushResult.UNAVAILABLE);
		}
	}

	private static PushResult toResult(SendResponse response) {
		if (response.isSuccessful()) {
			return PushResult.SENT;
		}
		FirebaseMessagingException e = response.getException();
		MessagingErrorCode code = e == null ? null : e.getMessagingErrorCode();
		PushResult result = resultOf(code);
		if (result != PushResult.UNREGISTERED) {
			log.warn("FCM 이 알림을 받지 않았습니다. errorCode={}, message={}", code, e == null ? null : e.getMessage());
		}
		return result;
	}

	/** FCM 오류 코드를 결과로 바꾼다. FCM 쪽 장애·한도는 토큰 문제가 아니므로 UNAVAILABLE. */
	static PushResult resultOf(MessagingErrorCode code) {
		if (code == null) {
			return PushResult.UNAVAILABLE;
		}
		return switch (code) {
			case UNREGISTERED -> PushResult.UNREGISTERED;
			case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> PushResult.UNAVAILABLE;
			default -> PushResult.FAILED;
		};
	}

	/** 인터럽트로 멈췄는지. 시간 초과(SocketTimeoutException)도 InterruptedIOException 이라 뺀다. */
	static boolean causedByInterrupt(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof InterruptedException
				|| (cause instanceof InterruptedIOException && !(cause instanceof SocketTimeoutException))) {
				return true;
			}
		}
		return Thread.currentThread().isInterrupted();
	}

	/**
	 * SDK 9.11 부터 {@code setToken} 은 deprecated 이고 새 FID(Firebase 설치 ID) 방식을 권한다. 앱은
	 * {@code getDevicePushTokenAsync()} 로 받은 FCM 등록 토큰을 보내고 노션 API-077 도 {@code message.token} 을 쓰므로
	 * 그대로 쓴다. FID 로 옮길 때는 앱·API-095 계약을 함께 바꾼다.
	 */
	@SuppressWarnings("deprecation")
	static Message toMessage(String token, PushMessage message) {
		return Message.builder()
			.setToken(token)
			.setNotification(Notification.builder().setTitle(message.title()).setBody(message.body()).build())
			.putAllData(message.data())
			.setAndroidConfig(AndroidConfig.builder()
				.setPriority(AndroidConfig.Priority.HIGH)
				.setNotification(AndroidNotification.builder()
					.setChannelId(message.channelId())
					.setTag(message.tag())
					.build())
				.build())
			.build();
	}

	@Override
	public void close() {
		app.delete();
	}
}
