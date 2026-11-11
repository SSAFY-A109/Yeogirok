import { useEffect, useMemo, useState } from "react";
import { Platform, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import type { VisitCell } from "@/features/statistics/model";
import { getLastKnownPosition } from "@/lib/native/location";
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

/** 내 위치를 알 수 없을 때(위치 권한이 없거나 기기가 아는 위치가 없을 때) 처음 보여 줄 곳. 시연 지역인 강릉 해변 일대다(TBD). */
const FALLBACK_CAMERA: MapCamera = { lat: 37.7801, lng: 128.9205, level: 7 };
/** 내 위치에서 지도를 열 때의 확대 단계(카카오 지도 level, 작을수록 가깝다). 내 위치 버튼과 같다. */
export const MY_LOCATION_LEVEL = 5;
/** 기기가 마지막으로 알던 위치를 이 시간 안에 못 읽으면 기본 지역에서 연다. 지도가 뜨지 않은 채 기다리지 않게 한다. */
const LAST_KNOWN_WAIT_MS = 1_500;

/**
 * 지도를 처음 어디에서 열지 정한다. 위치 권한이 있으면 기기가 마지막으로 알던 위치, 없으면 기본 지역이다.
 * 새로 잰 위치는 몇 초 뒤에 오므로 기다리지 않는다. 정해지기 전에는 `null`이고, 한 번 정한 뒤에는 바꾸지 않는다(바꾸면 지도를 다시 불러온다).
 */
function useInitialCamera(): MapCamera | null {
  const [camera, setCamera] = useState<MapCamera | null>(null);

  useEffect(() => {
    let isActive = true;

    const settle = (next: MapCamera) => {
      if (isActive) {
        setCamera((current) => {
          return current ?? next;
        });
      }
    };

    const timer = setTimeout(() => {
      settle(FALLBACK_CAMERA);
    }, LAST_KNOWN_WAIT_MS);

    void getLastKnownPosition().then((position) => {
      clearTimeout(timer);
      settle(position === null ? FALLBACK_CAMERA : { lat: position.latitude, lng: position.longitude, level: MY_LOCATION_LEVEL });
    });

    return () => {
      isActive = false;
      clearTimeout(timer);
    };
  }, []);

  return camera;
}

/** 발바닥 크기 배율. 화면에서 발자취가 가장 많은 칸이 1.25배, 거의 없는 칸이 0.75배이고 사이는 발자취 수에 비례한다(Figma W15). */
const MIN_FOOTPRINT_SCALE = 0.75;
const FOOTPRINT_SCALE_RANGE = 0.5;
/** 배지에 그대로 적는 최대 수. 넘으면 "999+"로 줄인다(Figma `Map / FootprintMarker` 설명). */
const MAX_BADGE_COUNT = 999;

function createFootprintStyle(): MapFootprintStyle {
  const colors = getColors("light");

  return {
    image: footprintPinImage.uri,
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

function toFootprintScale(count: number, maxCount: number) {
  const ratio = maxCount > 0 ? count / maxCount : 0;
  return Math.round((MIN_FOOTPRINT_SCALE + FOOTPRINT_SCALE_RANGE * ratio) * 100) / 100;
}

function toBadgeLabel(count: number) {
  return count > MAX_BADGE_COUNT ? `${MAX_BADGE_COUNT}+` : String(count);
}

function toFootprints(cells: readonly VisitCell[], selectedCellId: string | null): MapFootprint[] {
  const maxCount = Math.max(0, ...cells.map((cell) => {
    return cell.count;
  }));

  return cells.map((cell) => {
    return {
      id: cell.id,
      lat: cell.latitude,
      lng: cell.longitude,
      label: toBadgeLabel(cell.count),
      accessibilityLabel: `발자취 ${cell.count}개가 남은 곳`,
      scale: toFootprintScale(cell.count, maxCount),
      selected: cell.id === selectedCellId,
    };
  });
}

type VisitDistributionMapProps = {
  /** 서버가 묶은 격자 칸. 멀리 볼수록 칸이 커져 여러 칸이 하나로 묶여 온다. */
  cells: readonly VisitCell[];
  selectedCellId: string | null;
  /** 잰 내 위치. 권한이 없거나 아직 못 쟀으면 `null`. */
  currentLocation: MapCurrentLocation | null;
  focusRequest: MapFocusRequest | null;
  onPressCell: (cellId: string) => void;
  onPressMap: () => void;
  /** 사용자가 지도에 손을 댔을 때. 그 뒤로는 지도를 내 위치로 알아서 옮기지 않게 하는 데 쓴다. */
  onTouchMap: () => void;
  onRegionChange: (region: MapRegion) => void;
};

type MapPhase = "loading" | "ready" | "error";

/**
 * 서버가 묶은 격자 칸을 발바닥 마커로 그리는 카카오 지도(EXP-01·02). 웹이거나 지도 키가 없으면 안내만 보여 준다.
 * 위치 권한이 있으면 내 위치에서 연다(2026-11-11 사용자 요청).
 */
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
  cells,
  selectedCellId,
  currentLocation,
  focusRequest,
  onPressCell,
  onPressMap,
  onTouchMap,
  onRegionChange,
}: VisitDistributionMapProps & { javascriptKey: string }) {
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);
  const initialCamera = useInitialCamera();

  const footprints = useMemo(() => {
    return toFootprints(cells, selectedCellId);
  }, [cells, selectedCellId]);

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
    <View className="flex-1 bg-brand-muted" onTouchStart={phase === "ready" ? onTouchMap : undefined}>
      {initialCamera === null ? null : (
        <KakaoMapView
          key={attempt}
          accessibilityLabel="발자취가 많을수록 발바닥이 큰 방문 분포 지도"
          javascriptKey={javascriptKey}
          initialCamera={initialCamera}
          footprints={footprints}
          footprintStyle={FOOTPRINT_STYLE}
          currentLocation={currentLocation}
          currentLocationStyle={CURRENT_LOCATION_STYLE}
          focusRequest={focusRequest}
          onPressPoint={onPressCell}
          onPressMap={onPressMap}
          onRegionChange={onRegionChange}
          onReady={() => {
            setPhase("ready");
          }}
          onError={() => {
            setPhase("error");
          }}
        />
      )}
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
