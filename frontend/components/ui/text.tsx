import { Text as NativeText, type TextProps } from "react-native";

export function Text({ className, ...props }: TextProps) {
  return (
    <NativeText
      className={`font-sans text-body text-foreground ${className ?? ""}`}
      {...props}
    />
  );
}
