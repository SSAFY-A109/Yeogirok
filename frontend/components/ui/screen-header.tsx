import { router } from "expo-router";
import { ChevronLeft, type LucideIcon } from "lucide-react-native";
import type { ReactNode } from "react";
import { Pressable, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";

type HeaderIconButtonProps = {
  icon: LucideIcon;
  accessibilityLabel: string;
  onPress: () => void;
  testID?: string;
};

/** 헤더 오른쪽·왼쪽에 두는 44×44 아이콘 버튼. */
export function HeaderIconButton({ icon, accessibilityLabel, onPress, testID }: HeaderIconButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      className="h-touch w-touch items-center justify-center rounded-full active:bg-accent"
      onPress={onPress}
      testID={testID}
    >
      <Icon as={icon} className="text-icon" size={24} />
    </Pressable>
  );
}

type ScreenHeaderProps = {
  title: string;
  /** `center`: Figma `Navigation / AppBar`(가운데 제목). `start`: 여행방 상세처럼 뒤로가기 옆에 제목. */
  titleAlign?: "center" | "start";
  /** 왼쪽 요소. 기본은 뒤로가기다. 닫기 버튼을 오른쪽에 두는 화면은 `null`을 넘겨 자리만 비운다. */
  leading?: ReactNode;
  /** 오른쪽 요소(`HeaderIconButton`). 가운데 정렬이면 없을 때 같은 너비의 빈자리를 둔다. */
  trailing?: ReactNode;
  onBack?: () => void;
  testID?: string;
};

/**
 * 뒤로가기가 있는 화면의 상단 바(Figma `Navigation / AppBar`, 높이 56, 제목 Title/18).
 * 규칙 40은 제목을 `text-h1`로 두지만, 이번 Figma 시안(여행방 함께 장소 모으기)이 Title/18을 써서 시안을 따른다 `[확인]`.
 */
export function ScreenHeader({ title, titleAlign = "center", leading, trailing, onBack, testID }: ScreenHeaderProps) {
  const backButton = (
    <HeaderIconButton
      icon={ChevronLeft}
      accessibilityLabel="뒤로 가기"
      onPress={onBack ?? (() => {
        router.back();
      })}
      testID={testID ? `${testID}.back` : undefined}
    />
  );

  const leadingNode = leading === undefined ? backButton : leading ?? <View className="w-touch" />;
  const trailingNode = trailing ?? (titleAlign === "center" ? <View className="w-touch" /> : null);

  return (
    <View className={cn("h-14 flex-row items-center bg-card", titleAlign === "center" ? "justify-between px-2" : "px-1")} testID={testID}>
      {leadingNode}
      <Text
        accessibilityRole="header"
        className={cn("text-h3 text-foreground", titleAlign === "start" && "flex-1")}
        numberOfLines={1}
      >
        {title}
      </Text>
      {titleAlign === "start" ? <View className="flex-row">{trailingNode}</View> : trailingNode}
    </View>
  );
}
