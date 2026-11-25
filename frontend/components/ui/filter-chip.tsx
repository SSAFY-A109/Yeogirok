import type { LucideIcon } from "lucide-react-native";
import type { ComponentProps } from "react";
import { Pressable, View } from "react-native";

import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";
import { Icon } from "./icon";
import { Text } from "./text";

/** 칩 안 아이콘 크기(`icon-sm` 토큰과 같은 20). 글자(label 14)와 함께 보이는 크기다. */
const CHIP_ICON_SIZE = 20;
const colors = getColors("light");

/** 고른 칩을 칠하는 방식. `soft`는 연한 배경에 브랜드색 글자, `solid`는 브랜드색 배경에 흰 글자다. */
type ChipTone = "soft" | "solid";

type ChipIconProps = { icon: LucideIcon; selected: boolean; disabled: boolean; tone: ChipTone };

/**
 * 칩 아이콘. 고르면 면을 채우는데, 선과 면을 한 번에 그리면 나중에 그려지는 면이 먼저 그린 선을 덮는다
 * (예: 쇼핑백은 손잡이 다음에 가방 몸통을 그려 손잡이가 가려진다). 그래서 채운 면만 아래에 깔고, 그 위에 선 아이콘을 다시 그린다.
 * `solid`로 고른 칩은 배경이 브랜드색이라 면을 채우지 않고 흰 선만 그린다.
 */
function ChipIcon({ icon, selected, disabled, tone }: ChipIconProps) {
  const isSolidSelected = selected && !disabled && tone === "solid";
  const lineClassName = cn("text-icon", selected && "text-primary-pressed", isSolidSelected && "text-primary-foreground", disabled && "text-muted-foreground");

  if (!selected || disabled || isSolidSelected) {
    return <Icon as={icon} size={CHIP_ICON_SIZE} className={lineClassName} />;
  }

  return (
    <View className="h-icon-sm w-icon-sm">
      <View className="absolute inset-0">
        <Icon as={icon} size={CHIP_ICON_SIZE} fill={colors.accent} strokeWidth={0} />
      </View>
      <Icon as={icon} size={CHIP_ICON_SIZE} className={lineClassName} />
    </View>
  );
}

type FilterChipProps = Omit<ComponentProps<typeof Pressable>, "children"> & {
  children: string;
  size?: "sm" | "md";
  selected?: boolean;
  onSelectedChange?: (selected: boolean) => void;
  /** 글자에 더할 클래스. 격자 칸처럼 이름이 두 줄로 줄바꿈될 때 가운데 정렬에 쓴다. */
  textClassName?: string;
  /** 글자 앞(세로 배치면 위)에 두는 아이콘. 평소엔 회색 선, 고르면 브랜드색 선에 연한 하늘색 면을 채운다(사용자 요청 2026-11-06). */
  icon?: LucideIcon;
  /** 고른 칩을 칠하는 방식(기본 `soft`). 지도 위처럼 연한 배경이 묻히는 곳은 `solid`를 쓰고, 누르는 동안에도 배경을 채운다(사용자 요청 2026-11-13). */
  tone?: ChipTone;
};

/**
 * 선택 필터 칩(Figma `Selection / FilterChip`). 선택된 글자·아이콘은 `primary-pressed`(#145C83)다.
 * DESIGN.md의 `text-primary-hover`는 토큰으로 생성되지 않아 색이 비었으므로 같은 값의 `primary-pressed`를 쓴다.
 * 선택 배경 `bg-chip-selected`도 생성되지 않아, `bg-card`가 지워지고 배경이 투명해졌다. 선택된 칩 배경용 토큰 `brand-muted`를 쓴다.
 */
export function FilterChip({ children, className, textClassName, icon, tone = "soft", size = "sm", selected = false, disabled = false, onSelectedChange, onPress, ...props }: FilterChipProps) {
  const isSolid = tone === "solid";

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected, disabled }}
      hitSlop={{ top: size === "sm" ? 6 : 4, bottom: size === "sm" ? 6 : 4, left: 8, right: 8 }}
      className={cn(
        "items-center justify-center rounded-sm border border-border-strong bg-card px-3 py-1",
        size === "sm" ? "h-chip-sm" : "h-chip-md",
        isSolid && "active:bg-accent",
        selected && (isSolid ? "border-primary bg-primary active:bg-primary-pressed" : "border-ring bg-brand-muted"),
        disabled && "border-transparent bg-muted",
        className,
      )}
      disabled={disabled}
      onPress={(event) => {
        onSelectedChange?.(!selected);
        onPress?.(event);
      }}
      {...props}
    >
      {icon ? <ChipIcon icon={icon} selected={selected} disabled={Boolean(disabled)} tone={tone} /> : null}
      <Text className={cn("text-label text-foreground-secondary", selected && (isSolid ? "text-primary-foreground" : "text-primary-pressed"), disabled && "text-muted-foreground", textClassName)}>{children}</Text>
    </Pressable>
  );
}
