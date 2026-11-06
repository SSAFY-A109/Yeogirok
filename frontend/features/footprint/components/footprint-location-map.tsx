import { useMemo, useState, type ReactNode } from "react";
import { Platform, View } from "react-native";

import { Button } from "@/components/ui/button";
import { assets, DesignAsset } from "@/components/ui/design-asset";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import type { FootprintPosition } from "@/features/footprint/model";
import {
  footprintPinImage,
  KakaoMapView,
  type MapCamera,
  type MapCurrentLocation,
  type MapCurrentLocationStyle,
  type MapFocusRequest,
  type MapFootprint,
  type MapFootprintStyle,
} from "@/lib/native/map";
import { getColors } from "@/lib/theme";

/** Figma W16 `Location map` 높이. */
const MAP_HEIGHT = 170;
/** 정확도 원과 발바닥이 함께 보이는 확대 단계. */
const LOCATION_MAP_LEVEL = 3;
const javascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();
const colors = getColors("light");

const FOOTPRINT_STYLE: MapFootprintStyle = {
  image: footprintPinImage,
  badgeBackground: colors["highlight-muted"],
  badgeBorder: colors.card,
  badgeText: colors.highlight,
  selectionBorder: colors.ring,
  selectionFill: colors.card,
};

const CURRENT_LOCATION_STYLE: MapCurrentLocationStyle = {
  dot: colors["map-pin"],
  dotBorder: colors.card,
  halo: colors["map-route"],
  accuracy: colors["map-route"],
};

type MapPhase = "loading" | "ready" | "error";

/**
 * 발자취를 남길 곳(Figma W16 `Location map`). 잰 위치에 선택된 발바닥과 정확도 원을 그린다.
 * 다시 재면 새 위치로 옮긴다. 지도를 못 띄워도 저장은 할 수 있으므로 이 영역만 대체 안내로 바꾼다.
 */
export function FootprintLocationMap({ position }: { position: FootprintPosition }) {
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);
  const canLoad = Platform.OS !== "web" && Boolean(javascriptKey);

  const [initialCamera] = useState<MapCamera>(() => {
    return { lat: position.latitude, lng: position.longitude, level: LOCATION_MAP_LEVEL };
  });

  const currentLocation = useMemo<MapCurrentLocation>(() => {
    return { lat: position.latitude, lng: position.longitude, accuracyMeters: position.accuracyMeters, showDot: false };
  }, [position]);

  const footprints = useMemo<MapFootprint[]>(() => {
    return [{
      id: "here",
      lat: position.latitude,
      lng: position.longitude,
      label: "",
      accessibilityLabel: "발자취를 남길 곳",
      scale: 1,
      selected: true,
    }];
  }, [position]);

  const focusRequest = useMemo<MapFocusRequest>(() => {
    return { id: position.measuredAt.getTime(), lat: position.latitude, lng: position.longitude, level: LOCATION_MAP_LEVEL };
  }, [position]);

  if (!canLoad || !javascriptKey) {
    return (
      <LocationMapPlaceholder testID="footprint.map-unavailable">
        <PawImage />
        <Text className="text-caption text-foreground-secondary">지도는 Android·iOS 앱에서 볼 수 있어요</Text>
      </LocationMapPlaceholder>
    );
  }

  if (phase === "error") {
    return (
      <LocationMapPlaceholder testID="footprint.map-error">
        <PawImage />
        <Text className="text-caption text-foreground-secondary">지도를 불러오지 못했어요. 발자취는 그대로 남길 수 있어요.</Text>
        <Button
          variant="outline"
          className="h-9 px-3"
          accessibilityLabel="지도 다시 불러오기"
          onPress={() => {
            setPhase("loading");
            setAttempt(attempt + 1);
          }}
          testID="footprint.map-retry"
        >
          <Text>다시 시도</Text>
        </Button>
      </LocationMapPlaceholder>
    );
  }

  return (
    <View className="w-full bg-brand-muted" style={{ height: MAP_HEIGHT }} testID="footprint.map">
      <KakaoMapView
        key={attempt}
        accessibilityLabel="발자취를 남길 위치 지도"
        javascriptKey={javascriptKey}
        initialCamera={initialCamera}
        footprints={footprints}
        footprintStyle={FOOTPRINT_STYLE}
        currentLocation={currentLocation}
        currentLocationStyle={CURRENT_LOCATION_STYLE}
        focusRequest={focusRequest}
        onReady={() => {
          setPhase("ready");
        }}
        onError={() => {
          setPhase("error");
        }}
      />
      {phase === "loading" ? (
        <View accessibilityLabel="지도 불러오는 중" className="absolute inset-0 items-center justify-center gap-2 bg-brand-muted">
          <Spinner tone="brand" accessible={false} />
          <Text className="text-caption text-foreground-secondary">지도를 불러오고 있어요</Text>
        </View>
      ) : null}
    </View>
  );
}

/** 위치 지도 자리의 안내(위치 확인 중·실패, 지도 불가). 지도와 같은 높이를 지켜 아래 입력이 흔들리지 않게 한다. */
export function LocationMapPlaceholder({ children, testID }: { children: ReactNode; testID?: string }) {
  return (
    <View className="w-full items-center justify-center gap-2 bg-brand-muted px-5" style={{ height: MAP_HEIGHT }} testID={testID}>
      {children}
    </View>
  );
}

function PawImage() {
  return <DesignAsset source={assets.footprint} contentFit="contain" className="h-touch w-touch" accessible={false} />;
}
