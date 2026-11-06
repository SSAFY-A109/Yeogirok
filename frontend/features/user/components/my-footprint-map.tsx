import { useMemo, useState } from "react";
import { Platform, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { KakaoMapView, type MapCamera, type MapClusterBadge, type MapMarker } from "@/lib/native/map";
import { footprintPinImage } from "@/lib/native/map/footprint-pin";
import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";
import type { MyFootprint } from "../mocks/my-data";

const javascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();
const colors = getColors("light");
const clusterBadge: MapClusterBadge = { background: colors["highlight-muted"], foreground: colors.highlight, border: colors.card };
const MAP_HEIGHT = 176 * 1.3;
const GUESTBOOK_MAP_HEIGHT = 144 * 1.5;

type MyFootprintMapProps = { footprints: MyFootprint[]; selected?: boolean; onSelect?: (id: string) => void };

export function MyFootprintMap({ footprints, selected = false, onSelect }: MyFootprintMapProps) {
  const first = footprints[0];

  const mapKey = footprints.map(({ id, lat, lng }) => {
    return `${id}:${lat}:${lng}`;
  }).join(",");

  return (
    <View className={cn("relative overflow-hidden bg-muted", !selected && "rounded-md")} style={{ height: selected ? GUESTBOOK_MAP_HEIGHT : MAP_HEIGHT }} testID="my-footprints.map">
      {first ? <FootprintMapContent key={mapKey} center={first} footprints={footprints} onSelect={onSelect} /> : <View className="flex-1 items-center justify-center"><Text className="text-caption text-muted-foreground">표시할 발자취가 없어요</Text></View>}
    </View>
  );
}

function FootprintMapContent({ center, footprints, onSelect }: MyFootprintMapProps & { center: MyFootprint }) {
  const [phase, setPhase] = useState<"loading" | "ready" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const canLoad = Platform.OS !== "web" && Boolean(javascriptKey);

  const camera = useMemo<MapCamera>(() => {
    return { lat: center.lat, lng: center.lng, level: 4 };
  }, [center.lat, center.lng]);

  const markers = useMemo<MapMarker[]>(() => {
    return footprints.map(({ id, lat, lng }) => {
      return { id, lat, lng };
    });
  }, [footprints]);

  return (
    <View className="flex-1">
      {canLoad && javascriptKey && phase !== "error" ? (
        <>
          <KakaoMapView key={attempt} javascriptKey={javascriptKey} initialCamera={camera} markers={markers} markerImage={footprintPinImage} clusterBadge={clusterBadge} fitToMarkers accessibilityLabel={`내 발자취 ${footprints.length}곳 카카오 지도`} onReady={() => {
            setPhase("ready");
          }} onError={() => {
            setPhase("error");
          }} onPressPoint={(id) => {
            if (footprints.some((item) => {
              return item.id === id;
            })) {
              onSelect?.(id);
            }
          }} />
          {phase === "loading" && <View className="absolute inset-0 items-center justify-center gap-2 bg-muted" testID="my-footprints.map-loading"><Spinner tone="brand" accessible={false} /><Text className="text-caption text-foreground-secondary">카카오 지도를 불러오고 있어요</Text></View>}
        </>
      ) : <View className="flex-1 items-center justify-center gap-3 px-5" testID="my-footprints.map-error"><Text accessibilityRole="alert" className="text-center text-caption text-foreground-secondary">{Platform.OS === "web" ? "카카오 지도는 앱에서 확인해 주세요" : "카카오 지도를 불러오지 못했어요"}</Text>{canLoad && <Button variant="outline" testID="my-footprints.map-retry" onPress={() => {
        setPhase("loading");
        setAttempt(attempt + 1);
      }}><Text>다시 시도</Text></Button>}</View>}
    </View>
  );
}
