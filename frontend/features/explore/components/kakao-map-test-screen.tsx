import { isRunningInExpoGo } from "expo";
import { useEffect, useState } from "react";
import { ActivityIndicator, Platform, Pressable, ScrollView, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { Text } from "@/components/ui/text";
import { loadKakaoMap } from "@/lib/native/map";
import { getColors } from "@/lib/theme";

const nativeAppKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY?.trim();
const seoulCityHall = { lat: 37.5665, lng: 126.978, zoomLevel: 15 };

type MapState =
  | { phase: "loading" }
  | { phase: "error" }
  | ({ phase: "initialized" } & Awaited<ReturnType<typeof loadKakaoMap>>);

export function KakaoMapTestScreen() {
  const [state, setState] = useState<MapState>({ phase: "loading" });
  const [attempt, setAttempt] = useState(0);
  const isWeb = Platform.OS === "web";
  const isExpoGo = !isWeb && isRunningInExpoGo();
  const canLoad = !isWeb && !isExpoGo && Boolean(nativeAppKey);

  useEffect(() => {
    if (!canLoad || !nativeAppKey) return;
    let active = true;

    loadKakaoMap(nativeAppKey).then(
      (result) => {
        if (active) setState({ phase: "initialized", ...result });
      },
      () => {
        if (active) setState({ phase: "error" });
      },
    );

    return () => {
      active = false;
    };
  }, [attempt, canLoad]);

  let message = "지도를 준비하고 있어요.";
  if (isWeb) {
    message = "Android 또는 iOS 개발 빌드에서 열어주세요.";
  } else if (isExpoGo) {
    message = "카카오맵 모듈이 포함된 개발 빌드가 필요해요. Expo Go에서는 표시할 수 없어요.";
  } else if (!nativeAppKey) {
    message = ".env.local의 EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY를 입력한 후 개발 서버를 다시 시작하고 개발 빌드를 만들어주세요.";
  } else if (state.phase === "error") {
    message = "지도를 시작하지 못했어요. 네이티브 앱 키와 모듈 설치를 확인한 후 개발 빌드를 다시 만들어주세요.";
  } else if (state.phase === "initialized") {
    message = "SDK 초기화 완료. 아래에 서울시청 지도가 실제로 보이는지 확인해주세요. 인증·타일 표시 성공은 아직 자동 확인되지 않았어요.";
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
            {message}
          </Text>
          {canLoad && state.phase === "loading" && (
            <ActivityIndicator accessibilityLabel="지도 준비 중" color={getColors("light").primary} />
          )}
          {canLoad && state.phase === "error" && (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="지도 초기화 다시 시도"
              className="min-h-touch items-center justify-center rounded-md bg-primary px-4 active:bg-primary-pressed"
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
          {canLoad && state.phase === "initialized" ? (
            <state.MapView
              accessibilityLabel="서울시청 주변 카카오 지도"
              initialCamera={seoulCityHall}
              style={{ flex: 1 }}
            />
          ) : (
            <View className="flex-1 items-center justify-center p-5">
              <Text className="text-body-sm text-muted-foreground">지도가 표시될 영역</Text>
            </View>
          )}
        </View>

        <View className="gap-2">
          <Text className="text-h3">확인할 것</Text>
          <Text className="text-body-sm">1. 서울시청 주변 건물과 도로가 보이나요?</Text>
          <Text className="text-body-sm">2. 드래그로 이동하고 두 손가락으로 확대할 수 있나요?</Text>
          <Text className="text-body-sm">3. 화면을 나갔다 다시 열어도 지도가 보이나요?</Text>
          {Platform.OS === "android" && state.phase === "initialized" && (
            <View className="gap-2 rounded-lg border border-border p-4">
              <Text className="text-label">Android 키 해시</Text>
              <Text className="text-body-sm" selectable testID="kakao-map-test.key-hash">
                {state.keyHash ?? "키 해시를 읽지 못했어요. 빌드 서명 키에서 직접 추출해주세요."}
              </Text>
              <Text className="text-caption text-muted-foreground">
                카카오 콘솔에서 패키지명 com.ssafya109.yeogirok과 함께 등록해주세요.
              </Text>
            </View>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
