import { useState, type ComponentProps } from "react";
import { TextInput, View } from "react-native";

import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";
import { assets, DesignAsset } from "./design-asset";
import { Text } from "./text";

type SearchFieldProps = ComponentProps<typeof TextInput> & {
  error?: string;
  containerClassName?: string;
};

export function SearchField({ className, containerClassName, error, onFocus, onBlur, placeholder = "지역, 장소, 코스 검색", editable = true, ...props }: SearchFieldProps) {
  const [focused, setFocused] = useState(false);

  return (
    <View className={cn("gap-1", containerClassName)}>
      <View className={cn("h-button-md flex-row items-center gap-2 rounded-md border border-border bg-card px-3", focused && "border-ring", error && "border-destructive", !editable && "bg-muted")}>
        <DesignAsset source={assets.search} className="size-5" contentFit="contain" accessible={false} />
        <TextInput
          className={cn("h-full min-w-0 flex-1 py-0 font-sans text-body-sm text-foreground", !editable && "text-muted-foreground", className)}
          placeholder={placeholder}
          placeholderTextColor={getColors("light")["muted-foreground"]}
          accessibilityLabel={props.accessibilityLabel ?? placeholder}
          aria-invalid={Boolean(error)}
          editable={editable}
          returnKeyType="search"
          onFocus={(event) => { setFocused(true); onFocus?.(event); }}
          onBlur={(event) => { setFocused(false); onBlur?.(event); }}
          {...props}
        />
      </View>
      {error && <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-caption text-destructive">{error}</Text>}
    </View>
  );
}
