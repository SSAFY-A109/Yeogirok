import type { LucideIcon } from "lucide-react-native";
import type { ComponentProps } from "react";
import { Pressable } from "react-native";

import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";
import { Icon } from "./icon";
import { Text } from "./text";

/** 칩 안 아이콘 크기. 글자(label 14)와 함께 보이는 크기다. */
const CHIP_ICON_SIZE = 18;
const colors = getColors("light");

type FilterChipProps = Omit<ComponentProps<typeof Pressable>, "children"> & {
  children: string;
  size?: "sm" | "md";
  selected?: boolean;
  onSelectedChange?: (selected: boolean) => void;
  /** 글자에 더할 클래스. 격자 칸처럼 이름이 두 줄로 줄바꿈될 때 가운데 정렬에 쓴다. */
  textClassName?: string;
  /** 글자 앞(세로 배치면 위)에 두는 아이콘. 평소엔 회색 선, 고르면 브랜드색 선에 연한 하늘색 면을 채운다(사용자 요청 2026-11-06). */
  icon?: LucideIcon;
};

/**
 * 선택 필터 칩(Figma `Selection / FilterChip`). 선택된 글자·아이콘은 `primary-pressed`(#145C83)다.
 * DESIGN.md의 `text-primary-hover`는 토큰으로 생성되지 않아 색이 비었으므로 같은 값의 `primary-pressed`를 쓴다.
 */
export function FilterChip({ children, className, textClassName, icon, size = "sm", selected = false, disabled = false, onSelectedChange, onPress, ...props }: FilterChipProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected, disabled }}
      hitSlop={{ top: size === "sm" ? 6 : 4, bottom: size === "sm" ? 6 : 4, left: 8, right: 8 }}
      className={cn("items-center justify-center rounded-sm border border-border-strong bg-card px-3 py-1", size === "sm" ? "h-chip-sm" : "h-chip-md", selected && "border-ring bg-chip-selected", disabled && "border-transparent bg-muted", className)}
      disabled={disabled}
      onPress={(event) => {
        onSelectedChange?.(!selected);
        onPress?.(event);
      }}
      {...props}
    >
      {icon ? (
        <Icon
          as={icon}
          size={CHIP_ICON_SIZE}
          fill={selected && !disabled ? colors.accent : "none"}
          className={cn("text-icon", selected && "text-primary-pressed", disabled && "text-muted-foreground")}
        />
      ) : null}
      <Text className={cn("text-label text-foreground-secondary", selected && "text-primary-pressed", disabled && "text-muted-foreground", textClassName)}>{children}</Text>
    </Pressable>
  );
}
