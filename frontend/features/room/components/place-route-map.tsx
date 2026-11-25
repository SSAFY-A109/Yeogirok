import { useMemo, useState, type ReactNode } from "react";
import { FlatList, Platform, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { KakaoMapView, type MapCamera, type MapRoute } from "@/lib/native/map";
import { getColors } from "@/lib/theme";

const javascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();

/** 첫 장소를 가운데 두고 열 때의 확대 단계. 지도가 준비되면 모든 핀이 보이게 다시 맞춘다. */
const INITIAL_MAP_LEVEL = 7;

/** 홈 인디케이터가 없는 기기에서도 아래쪽 버튼이 화면 끝에 붙지 않게 두는 최소 여백. */
const MIN_BOTTOM_PADDING = 16;

type MapPhase = "loading" | "ready" | "error";

export type RoutePlace = {
  /** 목록 안에서 겹치지 않는 값. */
  id: string;
  name: string;
  /** 이름 아래 보조 줄(카테고리·지역, 도착 시각). 없으면 빈 문자열이다. */
  caption: string;
  coordinate: { latitude: number; longitude: number };
};

type PlaceRouteMapProps = {
  /** 번호를 매길 순서대로. 한 곳 이상이다. */
  places: readonly RoutePlace[];
  /** 방문 순서를 선으로 잇는다. 후보 장소처럼 순서가 없는 목록은 잇지 않는다. */
  connected: boolean;
  /** 지도 아래쪽 가운데에 띄울 요소("목록으로 보기" 버튼). 고른 장소 카드 아래에 둔다. */
  footer?: ReactNode;
  accessibilityLabel: string;
  testID: string;
};

/**
 * 장소들을 번호 핀으로 찍은 지도(TRP-07). 부모의 남은 높이를 채운다. 핀을 누르면 아래에 그 장소의 이름을 보여 주고, 지도를 누르면 닫는다.
 * 지도를 쓸 수 없으면(웹, 지도 키 없음, 불러오기 실패) 같은 장소를 번호 목록으로 보여 준다.
 */
export function PlaceRouteMap({ places, connected, footer, accessibilityLabel, testID }: PlaceRouteMapProps) {
  const insets = useSafeAreaInsets();
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);
  const [pressedId, setPressedId] = useState<string | null>(null);
  const canLoad = Platform.OS !== "web" && Boolean(javascriptKey);
  const bottomPadding = Math.max(insets.bottom, MIN_BOTTOM_PADDING);

  // 날을 바꿔 목록이 달라지면 앞서 누른 핀은 없는 것으로 본다.
  const selectedIndex = places.findIndex((place) => {
    return place.id === pressedId;
  });

  const selectedPlace = selectedIndex === -1 ? null : places[selectedIndex];

  const route = useMemo<MapRoute>(() => {
    const colors = getColors("light");

    return {
      connectPoints: connected,

      // 고른 핀이 없으면 모두 진하게, 있으면 고른 핀만 진하게 그린다.
      points: places.map((place, index) => {
        return {
          id: place.id,
          lat: place.coordinate.latitude,
          lng: place.coordinate.longitude,
          order: index + 1,
          selected: selectedIndex === -1 || index === selectedIndex,
        };
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
  }, [places, connected, selectedIndex]);

  const [initialCamera] = useState<MapCamera>(() => {
    const { latitude, longitude } = places[0].coordinate;
    return { lat: latitude, lng: longitude, level: INITIAL_MAP_LEVEL };
  });

  if (!canLoad || !javascriptKey || phase === "error") {
    return (
      <View className="flex-1" testID={`${testID}-fallback`}>
        <View className="items-center gap-3 px-5 py-4">
          <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">
            {canLoad ? "지도를 불러오지 못했어요. 장소를 목록으로 보여 드려요." : "이 기기에서는 지도를 볼 수 없어 목록으로 보여 드려요."}
          </Text>
          {canLoad ? (
            <Button
              variant="outline"

              onPress={() => {
                setPhase("loading");
                setAttempt(attempt + 1);
              }}

              testID={`${testID}-retry`}
            >
              <Text>다시 시도</Text>
            </Button>
          ) : null}
        </View>
        <FlatList
          className="flex-1"
          contentContainerClassName="px-5 pb-4"
          data={places}

          keyExtractor={(place) => {
            return place.id;
          }}

          renderItem={({ item, index }) => {
            return <PlaceSummary order={index + 1} place={item} />;
          }}
        />
        {footer === undefined ? null : (
          <View className="items-center pt-2" style={{ paddingBottom: bottomPadding }}>
            {footer}
          </View>
        )}
      </View>
    );
  }

  return (
    <View className="flex-1 bg-brand-muted" testID={testID}>
      <KakaoMapView
        key={attempt}
        accessibilityLabel={accessibilityLabel}
        javascriptKey={javascriptKey}
        initialCamera={initialCamera}
        route={route}
        onPressPoint={setPressedId}

        onPressMap={() => {
          setPressedId(null);
        }}

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
      <View pointerEvents="box-none" className="absolute bottom-0 left-0 right-0 items-center gap-3 px-5" style={{ paddingBottom: bottomPadding }}>
        {selectedPlace === null ? null : (
          <View accessibilityLiveRegion="polite" className="w-full rounded-lg border border-border bg-card px-3" testID={`${testID}-selected`}>
            <PlaceSummary order={selectedIndex + 1} place={selectedPlace} />
          </View>
        )}
        {footer}
      </View>
    </View>
  );
}

/** 번호와 장소 이름, 보조 줄. 지도에서 고른 장소 카드와 지도를 쓸 수 없을 때의 목록 행이 함께 쓴다. */
function PlaceSummary({ order, place }: { order: number; place: RoutePlace }) {
  return (
    <View accessible accessibilityLabel={[`${order}번`, place.name, place.caption].filter(Boolean).join(", ")} className="flex-row items-center gap-3 py-3">
      <View className="h-8 w-8 items-center justify-center rounded-full bg-primary">
        <Text className="text-label tabular-nums text-primary-foreground">{order}</Text>
      </View>
      <View className="flex-1 gap-1">
        <Text className="text-label text-foreground" numberOfLines={1}>
          {place.name}
        </Text>
        {place.caption === "" ? null : (
          <Text className="text-caption text-foreground-secondary" numberOfLines={1}>
            {place.caption}
          </Text>
        )}
      </View>
    </View>
  );
}
