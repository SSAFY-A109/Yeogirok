import { Send } from "lucide-react-native";
import { useState } from "react";
import { Pressable, TextInput, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Icon } from "@/components/ui/icon";
import type { ChatConnectionStatus } from "@/features/room/api/chat-connection";
import { CHAT_BODY_MAX_LENGTH, isSendableChatBody, limitChatBody } from "@/features/room/chat-model";
import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";

/** 홈 인디케이터가 없는 기기에서도 입력창이 화면 끝에 붙지 않게 두는 최소 아래 여백. */
const MIN_BOTTOM_PADDING = 12;

const READY_PLACEHOLDER = "메시지 보내기";
const NOT_READY_PLACEHOLDER = "실시간 연결을 준비하고 있어요";

type ChatComposerProps = {
  status: ChatConnectionStatus;
  /** 입력한 본문을 앞뒤 공백까지 그대로 넘긴다. */
  onSend: (body: string) => void;
};

/** 채팅 입력창(Figma W09-2 `Composer`). 실시간 연결이 준비됐을 때만 입력하고 보낼 수 있다. */
export function ChatComposer({ status, onSend }: ChatComposerProps) {
  const insets = useSafeAreaInsets();
  const [body, setBody] = useState("");
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
    <View
      className="flex-row items-center gap-2 border-t border-border bg-card px-4 pt-3"
      style={{ paddingBottom: Math.max(insets.bottom, MIN_BOTTOM_PADDING) }}
      testID="chat.composer"
    >
      <TextInput
        textAlignVertical="center"
        className={cn("h-touch min-w-0 flex-1 rounded-full bg-muted px-4 py-0 font-sans text-body-sm text-foreground", !ready && "text-muted-foreground")}
        value={body}

        onChangeText={(text) => {
          setBody(limitChatBody(text));
        }}

        placeholder={ready ? READY_PLACEHOLDER : NOT_READY_PLACEHOLDER}
        placeholderTextColor={getColors("light")["muted-foreground"]}
        accessibilityLabel="메시지 입력"
        accessibilityHint={ready ? `최대 ${CHAT_BODY_MAX_LENGTH}자까지 보낼 수 있어요` : NOT_READY_PLACEHOLDER}
        accessibilityState={{ disabled: !ready }}
        editable={ready}
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
  );
}
