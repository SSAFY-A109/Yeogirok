import { useMemo, useState } from "react";
import { Platform, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import type { VisitArea } from "@/features/statistics/model";
import {
  footprintPinImage,
  KakaoMapView,
  type MapCamera,
  type MapCurrentLocation,
  type MapCurrentLocationStyle,
  type MapFocusRequest,
  type MapFootprint,
  type MapFootprintStyle,
  type MapRegion,
} from "@/lib/native/map";
import { getColors } from "@/lib/theme";

const kakaoJavascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();

/** 현재 위치를 쓰기 전(발자취 남기기 단계)까지 처음 보여 줄 곳. 시연 지역인 강릉 해변 일대다(TBD). */
const INITIAL_CAMERA: MapCamera = { lat: 37.7801, lng: 128.9205, level: 7 };

/** 발바닥 크기 배율. 화면에서 가장 많이 다녀간 일대가 1.25배, 거의 없는 일대가 0.75배이고 사이는 인원수에 비례한다(Figma W15). */
const MIN_FOOTPRINT_SCALE = 0.75;
const FOOTPRINT_SCALE_RANGE = 0.5;

function createFootprintStyle(): MapFootprintStyle {
  const colors = getColors("light");

  return {
    image: footprintPinImage,
    badgeBackground: colors["highlight-muted"],
    badgeBorder: colors.card,
    badgeText: colors.highlight,
    selectionBorder: colors.ring,
    selectionFill: colors["brand-muted"],
  };
}

const FOOTPRINT_STYLE = createFootprintStyle();

function createCurrentLocationStyle(): MapCurrentLocationStyle {
  const colors = getColors("light");

  return {
    dot: colors["map-pin"],
    dotBorder: colors.card,
    halo: colors["map-route"],
    accuracy: colors["map-route"],
  };
}

/** 내 위치 점(Figma W15 `Current location`). 발자취 핀과 구분되는 파란 점이다. */
const CURRENT_LOCATION_STYLE = createCurrentLocationStyle();

function toFootprintScale(visitorCount: number, maxVisitorCount: number) {
  const ratio = maxVisitorCount > 0 ? visitorCount / maxVisitorCount : 0;
  return Math.round((MIN_FOOTPRINT_SCALE + FOOTPRINT_SCALE_RANGE * ratio) * 100) / 100;
}

function toFootprints(areas: readonly VisitArea[], selectedAreaId: string | null): MapFootprint[] {
  const maxVisitorCount = Math.max(0, ...areas.map((area) => {
    return area.visitorCount;
  }));

  return areas.map((area) => {
    return {
      id: area.id,
      lat: area.latitude,
      lng: area.longitude,
      label: String(area.visitorCount),
      accessibilityLabel: `${area.name}, 다녀간 사람 ${area.visitorCount}명`,
      scale: toFootprintScale(area.visitorCount, maxVisitorCount),
      selected: area.id === selectedAreaId,
    };
  });
}

type VisitDistributionMapProps = {
  areas: readonly VisitArea[];
  selectedAreaId: string | null;
  /** 잰 내 위치. 권한이 없거나 아직 못 쟀으면 `null`. */
  currentLocation: MapCurrentLocation | null;
  focusRequest: MapFocusRequest | null;
  onPressArea: (areaId: string) => void;
  onPressMap: () => void;
  onRegionChange: (region: MapRegion) => void;
};

type MapPhase = "loading" | "ready" | "error";

/** 서버가 집계한 일대를 발바닥 마커로 그리는 카카오 지도(EXP-01·02). 웹이거나 지도 키가 없으면 안내만 보여 준다. */
export function VisitDistributionMap(props: VisitDistributionMapProps) {
  if (Platform.OS === "web") {
    return <MapNotice message="지도는 Android·iOS 앱에서 볼 수 있어요." />;
  }

  if (!kakaoJavascriptKey) {
    return <MapNotice message="지도를 불러오지 못했어요." />;
  }

  return <VisitDistributionMapView javascriptKey={kakaoJavascriptKey} {...props} />;
}

function VisitDistributionMapView({
  javascriptKey,
  areas,
  selectedAreaId,
  currentLocation,
  focusRequest,
  onPressArea,
  onPressMap,
  onRegionChange,
}: VisitDistributionMapProps & { javascriptKey: string }) {
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);

  const footprints = useMemo(() => {
    return toFootprints(areas, selectedAreaId);
  }, [areas, selectedAreaId]);

  const retry = () => {
    setPhase("loading");

    setAttempt((value) => {
      return value + 1;
    });
  };

  if (phase === "error") {
    return (
      <View className="flex-1 items-center justify-center gap-4 bg-brand-muted px-5" testID="visit-map.map-error">
        <Text accessibilityRole="alert" className="text-center text-body text-foreground-secondary">
          지도를 불러오지 못했어요. 연결을 확인하고 다시 시도해 주세요.
        </Text>
        <Button variant="outline" onPress={retry} testID="visit-map.map-retry">
          <Text>다시 시도</Text>
        </Button>
      </View>
    );
  }

  return (
    <View className="flex-1 bg-brand-muted">
      <KakaoMapView
        key={attempt}
        accessibilityLabel="다녀간 사람이 많을수록 발바닥이 큰 방문 분포 지도"
        javascriptKey={javascriptKey}
        initialCamera={INITIAL_CAMERA}
        footprints={footprints}
        footprintStyle={FOOTPRINT_STYLE}
        currentLocation={currentLocation}
        currentLocationStyle={CURRENT_LOCATION_STYLE}
        focusRequest={focusRequest}
        onPressPoint={onPressArea}
        onPressMap={onPressMap}
        onRegionChange={onRegionChange}
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

function MapNotice({ message }: { message: string }) {
  return (
    <View className="flex-1 items-center justify-center bg-brand-muted px-5" testID="visit-map.map-unavailable">
      <Text className="text-center text-body text-foreground-secondary">{message}</Text>
    </View>
  );
}
