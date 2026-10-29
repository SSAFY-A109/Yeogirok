// vendor: react-native-reusables
// project: Figma input tokens, focus, and error message
import { useState, type ComponentProps } from "react";
import { TextInput, View } from "react-native";

import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";
import { Text } from "./text";

export type InputProps = ComponentProps<typeof TextInput> & {
  error?: string;
  containerClassName?: string;
};

function Input({ className, containerClassName, error, onFocus, onBlur, editable = true, placeholderTextColor = getColors("light")["muted-foreground"], ...props }: InputProps) {
  const [focused, setFocused] = useState(false);

  return (
    <View className={cn("w-full gap-1", containerClassName)}>
      <TextInput
        className={cn("h-input w-full min-w-0 rounded-sm border border-input bg-card px-4 py-3 font-sans text-body-sm text-foreground", focused && "border-2 border-ring", error && "border-destructive", !editable && "bg-muted text-muted-foreground", className)}
        placeholderTextColor={placeholderTextColor}
        accessibilityLabel={props.accessibilityLabel ?? props.placeholder}
        accessibilityHint={error || props.accessibilityHint}
        aria-invalid={Boolean(error)}
        editable={editable}
        onFocus={(event) => { setFocused(true); onFocus?.(event); }}
        onBlur={(event) => { setFocused(false); onBlur?.(event); }}
        {...props}
      />
      {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-caption text-destructive">{error}</Text>}
    </View>
  );
}

export { Input };
