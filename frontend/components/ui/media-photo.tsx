import { useQuery } from "@tanstack/react-query";
import { Image, type ImageProps } from "expo-image";
import { useEffect } from "react";
import { Platform, View } from "react-native";

import { getMockImageSource } from "@/features/media/mock-image";
import { cn } from "@/lib/utils";
import { Spinner } from "./spinner";

type MediaImageProps = Omit<ImageProps, "source"> & { uri: string };

export function MediaImage({ uri, ...props }: MediaImageProps) {
  if (__DEV__ && Platform.OS !== "web" && uri.startsWith("https://uploads.yeogirok.test/record-file/")) {
    return <MockRecordImage {...props} uri={uri} />;
  }

  return <Image {...props} source={{ uri }} />;
}

function MockRecordImage({ uri, onError, ...props }: MediaImageProps) {
  const source = useQuery({
    queryKey: ["mock-record-image", uri],

    queryFn: ({ signal }) => {
      return getMockImageSource(uri, signal);
    },

    staleTime: Infinity,
    gcTime: 0,
    retry: false,
  });

  useEffect(() => {
    if (source.isError) {
      onError?.({ error: source.error.message });
    }
  }, [onError, source.error, source.isError]);

  if (!source.data) {
    return source.isPending ? <View className="h-full w-full items-center justify-center">
      <Spinner tone="brand" accessibilityLabel="사진 불러오는 중" />
    </View> : null;
  }

  return <Image {...props} source={{ uri: source.data }} cachePolicy="none" onError={onError} />;
}

type MediaPhotoProps = {
  /** 장소·코스 대표 사진 주소. 없으면 옅은 브랜드 배경만 보인다. */
  uri: string | null;
  /** 크기·모서리. 기본 모서리는 `rounded-md`. */
  className?: string;
  accessibilityLabel?: string;
  cachePolicy?: ImageProps["cachePolicy"];
};

/** 사진 슬롯(Figma `Media / Photo`). 실제 이미지는 서버가 준 대표 사진으로 채운다. */
export function MediaPhoto({ uri, className, accessibilityLabel, cachePolicy }: MediaPhotoProps) {
  return (
    <View className={cn("overflow-hidden rounded-md bg-brand-muted", className)} accessible={Boolean(accessibilityLabel)} accessibilityLabel={accessibilityLabel}>
      {uri ? <MediaImage uri={uri} contentFit="cover" cachePolicy={cachePolicy} style={{ width: "100%", height: "100%" }} /> : null}
    </View>
  );
}
