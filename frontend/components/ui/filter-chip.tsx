import type { ComponentProps } from "react";
import { Pressable } from "react-native";

import { cn } from "@/lib/utils";
import { Text } from "./text";

type FilterChipProps = Omit<ComponentProps<typeof Pressable>, "children"> & {
  children: string;
  size?: "sm" | "md";
  selected?: boolean;
  onSelectedChange?: (selected: boolean) => void;
};

export function FilterChip({ children, className, size = "sm", selected = false, disabled = false, onSelectedChange, onPress, ...props }: FilterChipProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected, disabled }}
      hitSlop={{ top: size === "sm" ? 6 : 4, bottom: size === "sm" ? 6 : 4, left: 8, right: 8 }}
      className={cn("items-center justify-center rounded-sm border border-border-strong bg-card px-3", size === "sm" ? "h-chip-sm" : "h-chip-md", selected && "border-ring bg-chip-selected", disabled && "border-transparent bg-muted", className)}
      disabled={disabled}
      onPress={(event) => { onSelectedChange?.(!selected); onPress?.(event); }}
      {...props}
    >
      <Text className={cn("text-label text-foreground-secondary", selected && "text-primary-hover", disabled && "text-muted-foreground")}>{children}</Text>
    </Pressable>
  );
}
