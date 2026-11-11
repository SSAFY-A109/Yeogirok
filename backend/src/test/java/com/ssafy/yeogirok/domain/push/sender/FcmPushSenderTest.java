package com.ssafy.yeogirok.domain.push.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.google.firebase.messaging.MessagingErrorCode;

/** 서비스 계정 키를 읽지 못하면 시작을 멈추고, 오류에 키 내용을 담지 않는다. 실제 FCM 은 부르지 않는다. */
class FcmPushSenderTest {

	@Test
	void Base64_가_아니면_시작을_멈춘다() {
		assertThatThrownBy(() -> FcmPushSender.create(properties("secret-value-%%%")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("FIREBASE_SERVICE_ACCOUNT_BASE64")
			.hasMessageNotContaining("secret-value");
	}

	@Test
	void 서비스_계정_키_JSON_이_아니면_시작을_멈춘다() {
		String notAKey = Base64.getEncoder().encodeToString("{\"type\": \"secret-value\"}".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> FcmPushSender.create(properties(notAKey)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageNotContaining("secret-value");
	}

	@Test
	void 키가_비어_있으면_보내지_않는_발송기를_쓴다() {
		PushSender sender = new PushConfig().pushSender(properties(""));

		assertThat(sender).isInstanceOf(NoOpPushSender.class);
		assertThat(sender.send(java.util.List.of("a", "b"), null)).containsExactly(PushResult.FAILED, PushResult.FAILED);
	}

	@Test
	void 보내지_않는_발송기는_꺼져_있다고_알린다() {
		assertThat(new NoOpPushSender().isEnabled()).isFalse();
	}

	@Test
	void FCM_오류_코드를_토큰_문제와_FCM_장애로_나눈다() {
		assertThat(FcmPushSender.resultOf(MessagingErrorCode.UNREGISTERED)).isEqualTo(PushResult.UNREGISTERED);
		assertThat(FcmPushSender.resultOf(MessagingErrorCode.INVALID_ARGUMENT)).isEqualTo(PushResult.FAILED);
		assertThat(FcmPushSender.resultOf(MessagingErrorCode.SENDER_ID_MISMATCH)).isEqualTo(PushResult.FAILED);
		assertThat(FcmPushSender.resultOf(MessagingErrorCode.UNAVAILABLE)).isEqualTo(PushResult.UNAVAILABLE);
		assertThat(FcmPushSender.resultOf(MessagingErrorCode.INTERNAL)).isEqualTo(PushResult.UNAVAILABLE);
		assertThat(FcmPushSender.resultOf(MessagingErrorCode.QUOTA_EXCEEDED)).isEqualTo(PushResult.UNAVAILABLE);
		assertThat(FcmPushSender.resultOf(null)).isEqualTo(PushResult.UNAVAILABLE);
	}

	@Test
	void 인터럽트와_시간_초과를_구분한다() {
		assertThat(FcmPushSender.causedByInterrupt(new RuntimeException(new InterruptedIOException()))).isTrue();
		assertThat(FcmPushSender.causedByInterrupt(new RuntimeException(new InterruptedException()))).isTrue();
		assertThat(FcmPushSender.causedByInterrupt(new RuntimeException(new SocketTimeoutException()))).isFalse();
		assertThat(FcmPushSender.causedByInterrupt(new RuntimeException("네트워크"))).isFalse();
	}

	@Test
	void 설정을_출력해도_키는_보이지_않는다() {
		assertThat(properties("secret-value").toString()).doesNotContain("secret-value").contains("configured=true");
	}

	private static FcmProperties properties(String key) {
		return new FcmProperties(key, Duration.ofSeconds(5), Duration.ofSeconds(10));
	}
}
