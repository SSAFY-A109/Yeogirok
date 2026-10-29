import { useMemo, useState } from "react";
import { ActivityIndicator, Platform, Pressable, ScrollView, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { Text } from "@/components/ui/text";
import {
  KAKAO_JS_SDK_DOMAIN,
  KakaoMapView,
  type KakaoMapFailure,
  type MapCamera,
  type MapMarker,
  type MapPerformanceReport,
} from "@/lib/native/map";
import { getColors } from "@/lib/theme";

const javascriptKey = process.env.EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY?.trim();
const seoulCityHall: MapCamera = { lat: 37.5665, lng: 126.978, level: 3 };

const LOAD_TEST_MARKER_COUNTS = [0, 100, 500, 2000] as const;
const LOAD_TEST_SPREAD_DEGREES = 0.02;
const LOAD_TEST_SEED = 42;

type MarkersReport = Extract<MapPerformanceReport, { type: "markers" }>;
type InteractionReport = Extract<MapPerformanceReport, { type: "interaction" }>;

function createLoadTestMarkers(count: number): MapMarker[] {
  let seed = LOAD_TEST_SEED;
  const nextRandom = () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296;
    return seed / 4294967296;
  };
  const randomOffset = () => (nextRandom() * 2 - 1) * LOAD_TEST_SPREAD_DEGREES;

  return Array.from({ length: count }, () => ({
    lat: seoulCityHall.lat + randomOffset(),
    lng: seoulCityHall.lng + randomOffset(),
  }));
}

function describeMarkersReport(report: MarkersReport | null): string {
  if (!report) return "핀 개수를 골라주세요.";
  return `핀 ${report.count}개 그리기 ${report.renderMs}ms (앱에서 보낸 뒤 표시까지 ${report.totalMs}ms)`;
}

function describeInteractionReport(report: InteractionReport | null): string {
  if (!report) return "지도를 드래그하거나 확대·축소해 보세요.";
  const kind = report.kind === "drag" ? "드래그" : "확대·축소";
  const heap = report.heapMb === null ? "측정 불가" : `${report.heapMb}MB`;
  return `${kind} 평균 ${report.avgFps}fps · 가장 느린 프레임 ${report.worstFrameMs}ms · ${(report.durationMs / 1000).toFixed(1)}초 · 지도 메모리 ${heap}`;
}

type MapState = { phase: "loading" } | { phase: "ready" } | { phase: "error"; reason: KakaoMapFailure };

const failureMessages: Record<KakaoMapFailure, string> = {
  "sdk-load": `카카오 지도 스크립트를 불러오지 못했어요. 네트워크, JavaScript 키, 카카오 콘솔의 JavaScript SDK 도메인(${KAKAO_JS_SDK_DOMAIN}) 등록을 확인해주세요.`,
  "map-init": "지도를 만들지 못했어요. 다시 시도해주세요.",
  timeout: `지도 타일이 표시되지 않았어요. JavaScript 키와 JavaScript SDK 도메인(${KAKAO_JS_SDK_DOMAIN}) 등록, 네트워크를 확인한 뒤 다시 시도해주세요.`,
  network: "지도 화면을 열지 못했어요. 네트워크 연결을 확인한 뒤 다시 시도해주세요.",
  "no-response": "지도 페이지가 앱에 응답하지 않았어요. 다시 시도해도 같으면 아래 지도 영역에 무엇이 보이는지(빈 화면·지도·오류 문구) 확인해주세요.",
};

function getStatusMessage(state: MapState, isWeb: boolean): string {
  if (isWeb) return "Android 또는 iOS 앱에서 열어주세요.";
  if (!javascriptKey) {
    return ".env.local에 EXPO_PUBLIC_KAKAO_JAVASCRIPT_KEY를 입력한 후 개발 서버를 다시 시작해주세요.";
  }
  if (state.phase === "error") return failureMessages[state.reason];
  if (state.phase === "ready") return "지도 타일 표시 완료. 아래 지도가 드래그·확대되는지 확인해주세요.";
  return "지도를 불러오고 있어요.";
}

export function KakaoMapTestScreen() {
  const [state, setState] = useState<MapState>({ phase: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [markerCount, setMarkerCount] = useState<number | null>(null);
  const [markersReport, setMarkersReport] = useState<MarkersReport | null>(null);
  const [interactionReport, setInteractionReport] = useState<InteractionReport | null>(null);
  const markers = useMemo(
    () => (markerCount === null ? undefined : createLoadTestMarkers(markerCount)),
    [markerCount],
  );
  const isWeb = Platform.OS === "web";
  const canLoad = !isWeb && Boolean(javascriptKey);

  function handlePerformance(report: MapPerformanceReport) {
    if (report.type === "markers") setMarkersReport(report);
    if (report.type === "interaction") setInteractionReport(report);
  }

  return (
    <SafeAreaView className="flex-1 bg-background" edges={["bottom"]}>
      <ScrollView contentContainerClassName="gap-5 px-5 py-5">
        <View className="gap-2">
          <Text className="text-h1">카카오맵 연결 테스트</Text>
          <Text className="text-body-sm text-muted-foreground">
            서울시청 주변 지도로 표시·이동·확대를 확인해요. 현재 위치 권한은 사용하지 않아요.
          </Text>
        </View>

        <View className="gap-3 rounded-lg border border-border bg-muted p-4">
          <Text
            accessibilityLiveRegion="polite"
            className={state.phase === "error" && canLoad ? "text-body-sm text-destructive" : "text-body-sm"}
            testID="kakao-map-test.status"
          >
            {getStatusMessage(state, isWeb)}
          </Text>
          {canLoad && state.phase === "loading" && (
            <ActivityIndicator accessibilityLabel="지도 불러오는 중" color={getColors("light").primary} />
          )}
          {canLoad && state.phase === "error" && (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="지도 다시 불러오기"
              className="min-h-touch items-center justify-center rounded-sm bg-primary px-4 active:bg-primary-pressed"
              onPress={() => {
                setState({ phase: "loading" });
                setAttempt((value) => value + 1);
              }}
              testID="kakao-map-test.retry"
            >
              <Text className="text-button text-primary-foreground">다시 시도</Text>
            </Pressable>
          )}
        </View>

        <View className="aspect-square overflow-hidden rounded-lg border border-border bg-muted" testID="kakao-map-test.map">
          {canLoad && javascriptKey ? (
            <KakaoMapView
              key={attempt}
              accessibilityLabel="서울시청 주변 카카오 지도"
              initialCamera={seoulCityHall}
              javascriptKey={javascriptKey}
              markers={markers}
              onError={(reason) => setState({ phase: "error", reason })}
              onPerformance={handlePerformance}
              onReady={() => setState({ phase: "ready" })}
            />
          ) : (
            <View className="flex-1 items-center justify-center p-5">
              <Text className="text-body-sm text-muted-foreground">지도가 표시될 영역</Text>
            </View>
          )}
        </View>

        {canLoad && (
          <View className="gap-3 rounded-lg border border-border p-4" testID="kakao-map-test.load-test">
            <Text className="text-h3">부하 테스트</Text>
            <Text className="text-body-sm text-muted-foreground">
              핀 개수를 고른 뒤 지도를 드래그하고 확대·축소해 보세요. 서울시청 주변 약 4km 범위에 핀을 찍고, 지도 레벨
              5부터는 클러스터로 묶어요.
            </Text>
            <View className="flex-row gap-2">
              {LOAD_TEST_MARKER_COUNTS.map((count) => {
                const isSelected = markerCount === count;
                return (
                  <Pressable
                    key={count}
                    accessibilityRole="button"
                    accessibilityLabel={`핀 ${count}개 표시`}
                    accessibilityState={{ selected: isSelected }}
                    className={
                      isSelected
                        ? "min-h-touch flex-1 items-center justify-center rounded-sm bg-primary active:bg-primary-pressed"
                        : "min-h-touch flex-1 items-center justify-center rounded-sm bg-secondary active:bg-accent"
                    }
                    onPress={() => setMarkerCount(count)}
                    testID={`kakao-map-test.markers-${count}`}
                  >
                    <Text
                      className={
                        isSelected
                          ? "text-button tabular-nums text-primary-foreground"
                          : "text-button tabular-nums text-secondary-foreground"
                      }
                    >
                      {count}개
                    </Text>
                  </Pressable>
                );
              })}
            </View>
            <Text className="text-body-sm tabular-nums" testID="kakao-map-test.markers-report">
              {describeMarkersReport(markersReport)}
            </Text>
            <Text className="text-body-sm tabular-nums" testID="kakao-map-test.interaction-report">
              {describeInteractionReport(interactionReport)}
            </Text>
          </View>
        )}

        <View className="gap-2">
          <Text className="text-h3">확인할 것</Text>
          <Text className="text-body-sm">1. 서울시청 주변 건물과 도로가 보이나요?</Text>
          <Text className="text-body-sm">2. 드래그로 이동하고 두 손가락으로 확대할 수 있나요?</Text>
          <Text className="text-body-sm">3. 화면을 나갔다 다시 열어도 지도가 보이나요?</Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
