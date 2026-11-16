import { useState, type ReactNode } from "react";
import { Platform, ScrollView } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { cn } from "@/lib/utils";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./select";

export type DropdownOption = { value: string; label: string; disabled?: boolean };

type DropdownProps = {
  options: readonly DropdownOption[];
  value?: string;
  onValueChange: (value: string) => void;
  onOpenChange?: (open: boolean) => void;
  disabled?: boolean;
  placeholder?: string;
  accessibilityLabel: string;
  className?: string;
  testID?: string;
};

export function Dropdown({ options, value, onValueChange, onOpenChange, disabled = false, placeholder = "선택해 주세요", accessibilityLabel, className, testID }: DropdownProps) {
  const [width, setWidth] = useState(0);
  const insets = useSafeAreaInsets();

  const selected = options.find((option) => {
    return option.value === value;
  });

  return (
    <Select value={selected} onValueChange={(option) => {
      if (option) {
        onValueChange(option.value);
      }
    }} onOpenChange={onOpenChange} disabled={disabled} className={cn("w-full", className)}>
      <SelectTrigger accessibilityLabel={accessibilityLabel} testID={testID} onLayout={(event) => {
        return setWidth(event.nativeEvent.layout.width);
      }}>
        <SelectValue placeholder={placeholder} className={cn("min-w-0 flex-1 font-sans text-label", disabled && "text-muted-foreground")} />
      </SelectTrigger>
      <SelectContent insets={insets} sideOffset={4} style={width > 0 ? { width } : undefined}>
        <OptionList>
          {options.map((option) => {
            return (
              <SelectItem key={option.value} value={option.value} label={option.label} disabled={option.disabled} className={cn("min-h-touch", option.value === value && "bg-brand-muted")} />
            );
          })}
        </OptionList>
      </SelectContent>
    </Select>
  );
}

/**
 * 펼친 목록. 앱에서는 항목 5개(`min-h-touch` 44 × 5 + 간격 4 × 4 = 236)만큼만 보이고 나머지는 목록 안에서 스크롤한다(`max-h-60` 240).
 * 시·군·구(최대 30여 개)처럼 긴 목록이 화면 밖으로 넘치지 않게 한다. 웹은 벤더 목록이 이미 높이를 제한하고 스크롤한다.
 */
function OptionList({ children }: { children: ReactNode }) {
  if (Platform.OS === "web") {
    return <>{children}</>;
  }

  return (
    <ScrollView className="max-h-60" contentContainerClassName="gap-1" persistentScrollbar nestedScrollEnabled keyboardShouldPersistTaps="handled">
      {children}
    </ScrollView>
  );
}
