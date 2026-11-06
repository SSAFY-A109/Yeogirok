import { useMemo, useState, type ReactNode } from "react";
import { Platform, View } from "react-native";

import { Button } from "@/components/ui/button";
import { assets, DesignAsset } from "@/components/ui/design-asset";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import type { FootprintPoint, FootprintPosition } from "@/features/footprint/model";
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
/** 좌표로 지도 이동 요청 id를 만들 때의 자릿수(약 1m). 같은 곳이면 다시 옮기지 않는다. */
const FOCUS_ID_SCALE = 100_000;
const FOCUS_ID_LONGITUDE_SPAN = 100_000_000;

export const KAKAO_JAVASCRIPT_KEY = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim() ?? "";

/** 이 기기에서 카카오 지도를 띄울 수 있는지. 웹이거나 키가 없으면 지도 대신 안내를 보여 준다. */
export const canShowLocationMap = Platform.OS !== "web" && KAKAO_JAVASCRIPT_KEY !== "";

const colors = getColors("light");

const FOOTPRINT_STYLE: MapFootprintStyle = {
  image: footprintPinImage,
  badgeBackground: colors["highlight-muted"],
  badgeBorder: colors.card,
  badgeText: colors.highlight,
  selectionBorder: colors.ring,
  selectionFill: colors.card,
};

export const CURRENT_LOCATION_STYLE: MapCurrentLocationStyle = {
  dot: colors["map-pin"],
  dotBorder: colors.card,
  halo: colors["map-route"],
  accuracy: colors["map-route"],
};

export function isSamePoint(a: FootprintPoint, b: FootprintPoint): boolean {
  return a.latitude === b.latitude && a.longitude === b.longitude;
}

/** 좌표가 바뀔 때만 달라지는 지도 이동 요청 id. */
export function toFocusId({ latitude, longitude }: FootprintPoint): number {
  return Math.round(latitude * FOCUS_ID_SCALE) * FOCUS_ID_LONGITUDE_SPAN + Math.round(longitude * FOCUS_ID_SCALE);
}

type MapPhase = "loading" | "ready" | "error";

type FootprintLocationMapProps = {
  /** 기기가 잰 위치. 정확도 원을 그린다. */
  measured: FootprintPosition;
  /** 발자취를 남길 곳. 직접 고르지 않았으면 잰 위치와 같다. */
  point: FootprintPoint;
};

/**
 * 발자취를 남길 곳(Figma W16 `Location map`). 남길 곳에 선택된 발바닥을, 잰 위치에 정확도 원을 그린다.
 * 직접 고른 위치면 잰 위치에 파란 점을 함께 그려 얼마나 옮겼는지 보이게 한다. 다시 재거나 고르면 새 위치로 옮긴다.
 * 지도를 못 띄워도 저장은 할 수 있으므로 이 영역만 대체 안내로 바꾼다.
 */
export function FootprintLocationMap({ measured, point }: FootprintLocationMapProps) {
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);
  const isChosen = !isSamePoint(measured, point);

  const [initialCamera] = useState<MapCamera>(() => {
    return { lat: point.latitude, lng: point.longitude, level: LOCATION_MAP_LEVEL };
  });

  const currentLocation = useMemo<MapCurrentLocation>(() => {
    return { lat: measured.latitude, lng: measured.longitude, accuracyMeters: measured.accuracyMeters, showDot: isChosen };
  }, [measured, isChosen]);

  const footprints = useMemo<MapFootprint[]>(() => {
    return [{
      id: "here",
      lat: point.latitude,
      lng: point.longitude,
      label: "",
      accessibilityLabel: "발자취를 남길 곳",
      scale: 1,
      selected: true,
    }];
  }, [point]);

  const focusRequest = useMemo<MapFocusRequest>(() => {
    return { id: toFocusId(point), lat: point.latitude, lng: point.longitude, level: LOCATION_MAP_LEVEL };
  }, [point]);

  if (!canShowLocationMap) {
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
        javascriptKey={KAKAO_JAVASCRIPT_KEY}
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

export function PawImage() {
  return <DesignAsset source={assets.footprint} contentFit="contain" className="h-touch w-touch" accessible={false} />;
}
