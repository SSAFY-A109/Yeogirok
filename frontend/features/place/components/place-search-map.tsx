import { useMemo, useState } from "react";
import { Platform, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import type { Place } from "@/features/place/model";
import { KakaoMapView, type MapCamera, type MapMarker } from "@/lib/native/map";

const kakaoJavascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();

/** 첫 결과를 가운데 둘 때의 지도 확대 단계. 어댑터에 핀 영역 맞추기가 없어 고정값을 쓴다(TBD). */
const SEARCH_MAP_LEVEL = 4;

type MapStatus = "loading" | "ready" | "error";

type PlaceSearchMapViewProps = {
  javascriptKey: string;
  center: Place["coordinate"];
  places: readonly Place[];
};

/**
 * 검색 결과 좌표에 핀을 찍는 지도 미리보기(Figma W12 `Map wrap`).
 * 웹이거나 지도 키가 없으면 그리지 않는다. 검색어가 바뀌어 첫 결과가 달라지면 지도를 새로 연다.
 */
export function PlaceSearchMap({ places }: { places: readonly Place[] }) {
  const firstPlace = places[0];

  if (Platform.OS === "web" || !kakaoJavascriptKey || firstPlace === undefined) {
    return null;
  }

  const { latitude, longitude } = firstPlace.coordinate;

  return (
    <PlaceSearchMapView
      key={`${latitude},${longitude}`}
      javascriptKey={kakaoJavascriptKey}
      center={firstPlace.coordinate}
      places={places}
    />
  );
}

function PlaceSearchMapView({ javascriptKey, center, places }: PlaceSearchMapViewProps) {
  const [status, setStatus] = useState<MapStatus>("loading");
  const [attempt, setAttempt] = useState(0);
  const { latitude, longitude } = center;

  const camera = useMemo<MapCamera>(() => {
    return { lat: latitude, lng: longitude, level: SEARCH_MAP_LEVEL };
  }, [latitude, longitude]);

  const markers = useMemo<MapMarker[]>(() => {
    return places.map((place) => {
      return { lat: place.coordinate.latitude, lng: place.coordinate.longitude };
    });
  }, [places]);

  const retry = () => {
    setStatus("loading");

    setAttempt((value) => {
      return value + 1;
    });
  };

  return (
    <View className="mx-5 mt-4 h-36 overflow-hidden rounded-md bg-brand-muted" testID="place-search.map">
      {status === "error" ? (
        <View className="flex-1 items-center justify-center gap-2 px-5">
          <Text accessibilityRole="alert" className="text-center text-caption text-foreground-secondary">
            지도를 불러오지 못했어요.
          </Text>
          <Button variant="outline" onPress={retry} testID="place-search.map-retry">
            <Text>다시 시도</Text>
          </Button>
        </View>
      ) : (
        <>
          <KakaoMapView
            key={attempt}
            javascriptKey={javascriptKey}
            initialCamera={camera}
            markers={markers}
            onReady={() => {
              setStatus("ready");
            }}
            onError={() => {
              setStatus("error");
            }}
            accessibilityLabel={`검색 결과 ${places.length}곳 지도`}
          />
          {status === "loading" ? <Skeleton accessible={false} className="absolute inset-0 rounded-none" /> : null}
        </>
      )}
    </View>
  );
}
