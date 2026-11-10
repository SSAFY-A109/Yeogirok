import { useMemo, useState } from "react";
import { Platform, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { KakaoMapView, type MapCamera, type MapRoute } from "@/lib/native/map";
import { getColors } from "@/lib/theme";
import type { CoursePlace } from "@/features/course/model";
import { RouteMapPreview, type MapPin } from "./route-map-preview";

const MAP_HEIGHT = 196;
const ROUTE_MAP_LEVEL = 7;
const FALLBACK_PADDING = 0.12;
const javascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();

type CourseRouteMapProps = {
  places: Pick<CoursePlace, "id" | "name" | "coordinate">[];
  selectedIds?: ReadonlySet<string>;
  connectPlaces?: boolean;
  onPressPlace?: (placeId: string) => void;
  testID?: string;
  accessibilityLabel?: string;
};

type MapPhase = "loading" | "ready" | "error";

// 코스 장소를 카카오맵에 번호 핀으로 찍고 순서대로 선으로 잇는다(EXP-05·07). 지도를 쓸 수 없으면 간략 경로로 대신한다.
export function CourseRouteMap({
  places, selectedIds, connectPlaces = true, onPressPlace, testID = "course-detail.map", accessibilityLabel = "코스 장소를 순서대로 이은 지도",
}: CourseRouteMapProps) {
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);
  const canLoad = Platform.OS !== "web" && Boolean(javascriptKey);

  const route = useMemo<MapRoute>(() => {
    const colors = getColors("light");

    return {
      connectPoints: connectPlaces,

      points: places.map((place, index) => {
        return { id: place.id, lat: place.coordinate.latitude, lng: place.coordinate.longitude, order: index + 1, selected: selectedIds?.has(place.id) ?? true };
      }),

      colors: {
        line: colors["map-route"],
        pinSelected: colors.primary,
        pinSelectedText: colors["primary-foreground"],
        pinSelectedBorder: colors.card,
        pinDefault: colors.card,
        pinDefaultText: colors["foreground-secondary"],
        pinBorder: colors["border-strong"],
      },
    };
  }, [places, selectedIds, connectPlaces]);

  const [initialCamera] = useState<MapCamera>(() => {
    const first = places[0];

    return { lat: first?.coordinate.latitude ?? 37.7519, lng: first?.coordinate.longitude ?? 128.8761, level: ROUTE_MAP_LEVEL };
  });

  if (canLoad && javascriptKey && phase !== "error") {
    return (
      <View className="w-full overflow-hidden bg-brand-muted" style={{ height: MAP_HEIGHT }} testID={testID}>
        <KakaoMapView
          key={attempt}
          accessibilityLabel={accessibilityLabel}
          javascriptKey={javascriptKey}
          initialCamera={initialCamera}
          route={route}
          onPressPoint={onPressPlace}
          onReady={() => {
            setPhase("ready");
          }}
          onError={() => {
            setPhase("error");
          }}
        />
        {phase === "loading" && (
          <View accessibilityLabel="지도 불러오는 중" className="absolute inset-0 items-center justify-center gap-2 bg-brand-muted">
            <Spinner tone="brand" accessible={false} />
            <Text className="text-caption text-foreground-secondary">지도를 불러오고 있어요</Text>
          </View>
        )}
      </View>
    );
  }

  return (
    <View testID={`${testID}-fallback`}>
      <RouteMapPreview pins={toPreviewPins(places, selectedIds)} connectPins={connectPlaces} onPressPin={onPressPlace} />
      <View className="flex-row items-center justify-between gap-3 bg-brand-muted px-5 pb-3">
        <Text className="flex-1 text-caption text-foreground-secondary">{connectPlaces ? "지도를 불러오지 못해 간략 경로로 보여 드려요" : "지도를 불러오지 못해 위치를 간략하게 표시해요"}</Text>
        {canLoad && (
          <Button
            variant="outline"
            className="h-9 px-3"
            accessibilityLabel="지도 다시 불러오기"
            onPress={() => {
              setPhase("loading");
              setAttempt(attempt + 1);
            }}
            testID={`${testID}-retry`}
          >
            <Text>다시 시도</Text>
          </Button>
        )}
      </View>
    </View>
  );
}

function toPreviewPins(places: CourseRouteMapProps["places"], selectedIds: CourseRouteMapProps["selectedIds"]): MapPin[] {
  const lats = places.map((place) => {
    return place.coordinate.latitude;
  });

  const lngs = places.map((place) => {
    return place.coordinate.longitude;
  });

  const minLat = Math.min(...lats);
  const minLng = Math.min(...lngs);
  const latRange = Math.max(Math.max(...lats) - minLat, Number.EPSILON);
  const lngRange = Math.max(Math.max(...lngs) - minLng, Number.EPSILON);
  const span = 1 - FALLBACK_PADDING * 2;

  return places.map((place, index) => {
    return {
      id: place.id,
      order: index + 1,
      label: place.name,
      x: FALLBACK_PADDING + ((place.coordinate.longitude - minLng) / lngRange) * span,
      y: FALLBACK_PADDING + (1 - (place.coordinate.latitude - minLat) / latRange) * span,
      selected: selectedIds?.has(place.id) ?? true,
    };
  });
}
