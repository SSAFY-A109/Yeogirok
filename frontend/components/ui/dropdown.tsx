import { useState } from "react";
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
  const selected = options.find((option) => option.value === value);

  return (
    <Select value={selected} onValueChange={(option) => { if (option) onValueChange(option.value); }} onOpenChange={onOpenChange} disabled={disabled} className={cn("w-full", className)}>
      <SelectTrigger accessibilityLabel={accessibilityLabel} testID={testID} onLayout={(event) => setWidth(event.nativeEvent.layout.width)}>
        <SelectValue placeholder={placeholder} className={cn("min-w-0 flex-1 font-sans text-label", disabled && "text-muted-foreground")} />
      </SelectTrigger>
      <SelectContent insets={insets} sideOffset={4} style={width > 0 ? { width } : undefined}>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value} label={option.label} disabled={option.disabled} className={cn("min-h-touch", option.value === value && "bg-brand-muted")} />
        ))}
      </SelectContent>
    </Select>
  );
}
