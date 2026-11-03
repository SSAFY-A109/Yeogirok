import { View } from "react-native";
import { ImageIcon } from "lucide-react-native";

import { Icon } from "@/components/ui/icon";
import { cn } from "@/lib/utils";

export type PhotoTone = "brand" | "accent" | "coral" | "muted";

const toneClassNames: Record<PhotoTone, string> = {
  brand: "bg-brand-muted",
  accent: "bg-accent",
  coral: "bg-highlight-muted",
  muted: "bg-muted",
};

const tones = Object.keys(toneClassNames) as PhotoTone[];

export function toneAt(index: number): PhotoTone {
  return tones[index % tones.length] ?? "muted";
}

type PhotoPlaceholderProps = {
  size?: number;
  tone?: PhotoTone;
  className?: string;
  iconSize?: number;
};

// 사진 API가 없어 자리만 잡는다. 실제 사진이 오면 expo-image로 교체한다.
export function PhotoPlaceholder({ size, tone = "brand", className, iconSize = 24 }: PhotoPlaceholderProps) {
  return (
    <View
      accessible={false}
      importantForAccessibility="no-hide-descendants"
      className={cn("shrink-0 items-center justify-center rounded-md", toneClassNames[tone], className)}
      style={size ? { width: size, height: size } : undefined}
    >
      <Icon as={ImageIcon} size={iconSize} className="text-muted-foreground" />
    </View>
  );
}
