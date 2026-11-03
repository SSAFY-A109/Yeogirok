import { useMemo, useState } from "react";
import { Platform, View } from "react-native";

import { Button } from "@/components/ui/button";
import { DesignAsset } from "@/components/ui/design-asset";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { KakaoMapView, placePinImage, type MapCamera, type MapMarker } from "@/lib/native/map";
import type { RankedPlace } from "../model";

const MAP_HEIGHT = 160;
const PLACE_MAP_LEVEL = 4;
const javascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();

type MapPhase = "loading" | "ready" | "error";

// 장소 한 곳의 위치를 카카오맵 마커로 보여 준다. 지도를 쓸 수 없으면 지역 이름으로 대신한다.
export function PlaceLocationMap({ place }: { place: RankedPlace }) {
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);
  const canLoad = Platform.OS !== "web" && Boolean(javascriptKey);

  const camera = useMemo<MapCamera>(() => {
    return { lat: place.lat, lng: place.lng, level: PLACE_MAP_LEVEL };
  }, [place.lat, place.lng]);

  const markers = useMemo<MapMarker[]>(() => {
    return [{ lat: place.lat, lng: place.lng }];
  }, [place.lat, place.lng]);

  if (canLoad && javascriptKey && phase !== "error") {
    return (
      <View className="w-full overflow-hidden rounded-lg bg-brand-muted" style={{ height: MAP_HEIGHT }} testID="place-sheet.map">
        <KakaoMapView
          key={attempt}
          accessibilityLabel={`${place.name} 위치 지도`}
          javascriptKey={javascriptKey}
          initialCamera={camera}
          markers={markers}
          markerImage={placePinImage}
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
    <View className="w-full items-center justify-center gap-2 rounded-lg bg-brand-muted px-5" style={{ height: MAP_HEIGHT }} testID="place-sheet.map-fallback">
      <DesignAsset
        source={require("@/assets/images/map/place-pin.png")}
        contentFit="contain"
        style={{ width: placePinImage.width, height: placePinImage.height }}
        accessible={false}
      />
      <Text className="text-label text-foreground">{place.area}</Text>
      <Text className="text-caption text-foreground-secondary">지도를 불러오지 못했어요</Text>
      {canLoad && (
        <Button
          variant="outline"
          className="h-9 px-3"
          accessibilityLabel="지도 다시 불러오기"
          onPress={() => {
            setPhase("loading");
            setAttempt(attempt + 1);
          }}
          testID="place-sheet.map-retry"
        >
          <Text>다시 시도</Text>
        </Button>
      )}
    </View>
  );
}
