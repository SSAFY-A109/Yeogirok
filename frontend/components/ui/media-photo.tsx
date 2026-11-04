import { Image } from "expo-image";
import { View } from "react-native";

import { cn } from "@/lib/utils";

type MediaPhotoProps = {
  /** 장소·코스 대표 사진 주소. 없으면 옅은 브랜드 배경만 보인다. */
  uri: string | null;
  /** 크기·모서리. 기본 모서리는 `rounded-md`. */
  className?: string;
  accessibilityLabel?: string;
};

/** 사진 슬롯(Figma `Media / Photo`). 실제 이미지는 서버가 준 대표 사진으로 채운다. */
export function MediaPhoto({ uri, className, accessibilityLabel }: MediaPhotoProps) {
  return (
    <View className={cn("overflow-hidden rounded-md bg-brand-muted", className)} accessible={Boolean(accessibilityLabel)} accessibilityLabel={accessibilityLabel}>
      {uri ? <Image source={{ uri }} contentFit="cover" style={{ width: "100%", height: "100%" }} /> : null}
    </View>
  );
}
