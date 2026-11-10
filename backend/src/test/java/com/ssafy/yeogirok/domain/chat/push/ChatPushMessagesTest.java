package com.ssafy.yeogirok.domain.chat.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.push.sender.PushMessage;

class ChatPushMessagesTest {

	@Test
	void 제목은_방_이름_본문은_닉네임과_메시지_data_는_문자열_값() {
		PushMessage message = ChatPushMessages.of(101L, 111L, "강릉 우정여행", "민지", "내일 아침 9시에 숙소 앞에서 만나자!", 40);

		assertThat(message.title()).isEqualTo("강릉 우정여행");
		assertThat(message.body()).isEqualTo("민지: 내일 아침 9시에 숙소 앞에서 만나자!");
		assertThat(message.data()).isEqualTo(Map.of("type", "CHAT", "roomId", "101", "messageId", "111"));
		assertThat(message.channelId()).isEqualTo("chat");
		assertThat(message.tag()).isEqualTo("chat:101");
	}

	@Test
	void 사십자까지는_그대로_넘으면_자르고_말줄임표를_붙인다() {
		String forty = "가".repeat(40);
		assertThat(ChatPushMessages.preview(forty, 40)).isEqualTo(forty);
		assertThat(ChatPushMessages.preview(forty + "나", 40)).isEqualTo(forty + "…");
	}

	@Test
	void 글자_수는_코드_포인트로_세어_이모지를_반으로_자르지_않는다() {
		String emoji = "😀".repeat(41); // 한 글자가 UTF-16 두 칸
		String preview = ChatPushMessages.preview(emoji, 40);

		assertThat(preview).isEqualTo("😀".repeat(40) + "…");
		assertThat(ChatPushMessages.preview("😀".repeat(40), 40)).isEqualTo("😀".repeat(40));
	}
}
