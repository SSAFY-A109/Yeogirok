import { Send } from "lucide-react-native";
import { useState } from "react";
import { Pressable, TextInput, View } from "react-native";
import Animated, { useAnimatedKeyboard, useAnimatedStyle } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Icon } from "@/components/ui/icon";
import type { ChatConnectionStatus } from "@/features/room/api/chat-session";
import { CHAT_BODY_MAX_LENGTH, isSendableChatBody, limitChatBody } from "@/features/room/chat-model";
import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";

/** 입력창과 그 아래(화면 끝 또는 키보드) 사이에 두는 최소 여백. 홈 인디케이터가 없는 기기에서도 화면 끝에 붙지 않는다. */
const MIN_BOTTOM_PADDING = 12;

const READY_PLACEHOLDER = "메시지 보내기";
const NOT_READY_PLACEHOLDER = "대화에 연결하고 있어요";

type ChatComposerProps = {
  status: ChatConnectionStatus;
  /** 입력한 본문을 앞뒤 공백까지 그대로 넘긴다. */
  onSend: (body: string) => void;
};

/**
 * 채팅 입력창(Figma W09-2 `Composer`). 글은 언제든 쓸 수 있고, 보내기는 실시간 연결이 준비됐을 때만 된다.
 * 연결이 잠깐 끊겨도 쓰던 글과 키보드를 그대로 둔다.
 * 키보드가 올라오면 그 높이만큼 아래 여백을 늘려 키보드 바로 위에 붙는다. 화면이 시스템 바 아래까지 그려지는
 * Android(edge-to-edge)에서는 키보드가 떠도 창 크기가 줄지 않아, `KeyboardAvoidingView`로는 배포 빌드에서 입력창이 가려졌다.
 * 커서는 줄높이만큼 그려지므로, 입력창만 줄높이를 `leading-4`(16px)로 줄여 커서를 14px 글자 높이에 맞춘다.
 */
export function ChatComposer({ status, onSend }: ChatComposerProps) {
  const insets = useSafeAreaInsets();
  const keyboard = useAnimatedKeyboard();
  const [body, setBody] = useState("");
  const restingBottomSpace = Math.max(insets.bottom, MIN_BOTTOM_PADDING);

  const bottomSpaceStyle = useAnimatedStyle(() => {
    return { height: Math.max(keyboard.height.get() + MIN_BOTTOM_PADDING, restingBottomSpace) };
  });

  const ready = status === "READY";
  const canSend = ready && isSendableChatBody(body);

  const send = () => {
    if (!canSend) {
      return;
    }

    onSend(body);
    setBody("");
  };

  return (
    <View className="border-t border-border bg-card" testID="chat.composer">
      <View className="flex-row items-center gap-2 px-4 pt-3">
        <TextInput
          textAlignVertical="center"
          className="h-touch min-w-0 flex-1 rounded-full bg-muted px-4 py-0 font-sans text-body-sm leading-4 text-foreground"
          value={body}

          onChangeText={(text) => {
            setBody(limitChatBody(text));
          }}

          placeholder={ready ? READY_PLACEHOLDER : NOT_READY_PLACEHOLDER}
          placeholderTextColor={getColors("light")["muted-foreground"]}
          accessibilityLabel="메시지 입력"
          accessibilityHint={ready ? `최대 ${CHAT_BODY_MAX_LENGTH}자까지 보낼 수 있어요` : NOT_READY_PLACEHOLDER}
          returnKeyType="send"
          submitBehavior="submit"
          onSubmitEditing={send}
          testID="chat.input"
        />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="메시지 보내기"
          accessibilityState={{ disabled: !canSend }}
          className={cn("h-touch w-touch items-center justify-center rounded-full", canSend ? "bg-primary active:bg-primary-pressed" : "bg-disabled")}
          disabled={!canSend}
          onPress={send}
          testID="chat.send"
        >
          <Icon as={Send} size={20} className={canSend ? "text-primary-foreground" : "text-disabled-foreground"} />
        </Pressable>
      </View>
      <Animated.View style={bottomSpaceStyle} />
    </View>
  );
}
