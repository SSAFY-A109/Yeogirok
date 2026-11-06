import type { LucideIcon } from "lucide-react-native";
import type { ComponentProps } from "react";
import { Pressable } from "react-native";

import { cn } from "@/lib/utils";
import { Icon } from "./icon";
import { Text } from "./text";

/** 칩 안 아이콘 크기. 글자(label 14)와 함께 보이는 크기다. */
const CHIP_ICON_SIZE = 18;

type FilterChipProps = Omit<ComponentProps<typeof Pressable>, "children"> & {
  children: string;
  size?: "sm" | "md";
  selected?: boolean;
  onSelectedChange?: (selected: boolean) => void;
  /** 글자에 더할 클래스. 격자 칸처럼 이름이 두 줄로 줄바꿈될 때 가운데 정렬에 쓴다. */
  textClassName?: string;
  /** 글자 앞(세로 배치면 위)에 두는 아이콘. 예: 카테고리 아이콘(Figma `카테고리 기준 (v3)`). */
  icon?: LucideIcon;
};

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
        <Icon as={icon} size={CHIP_ICON_SIZE} className={cn("text-icon", selected && "text-primary-hover", disabled && "text-muted-foreground")} />
      ) : null}
      <Text className={cn("text-label text-foreground-secondary", selected && "text-primary-hover", disabled && "text-muted-foreground", textClassName)}>{children}</Text>
    </Pressable>
  );
}
