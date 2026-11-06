import { X } from "lucide-react-native";
import { useMemo, useState } from "react";
import { Modal, View } from "react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { HeaderIconButton, ScreenHeader } from "@/components/ui/screen-header";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import {
  CURRENT_LOCATION_STYLE,
  KAKAO_JAVASCRIPT_KEY,
  PawImage,
} from "@/features/footprint/components/footprint-location-map";
import { distanceMeters, formatDistance, type FootprintPoint, type FootprintPosition } from "@/features/footprint/model";
import { KakaoMapView, type MapCamera, type MapCurrentLocation, type MapFocusRequest, type MapRegion } from "@/lib/native/map";

/** 위치를 고를 때의 확대 단계. 골목 단위로 맞출 수 있게 가깝게 본다. */
const PICKER_LEVEL = 3;

type MapPhase = "loading" | "ready" | "error";

type FootprintPointPickerProps = {
  visible: boolean;
  /** 기기가 잰 위치. 파란 점과 정확도 원으로 보여 준다. */
  measured: FootprintPosition;
  /** 처음 맞출 곳. 이미 고른 위치가 있으면 그곳, 없으면 잰 위치다. */
  initialPoint: FootprintPoint;
  onConfirm: (point: FootprintPoint) => void;
  onClose: () => void;
};

/**
 * 발자취를 남길 위치를 지도에서 직접 고르는 화면(2026-11-06 사용자 결정, 거리 제한 없음, Figma 시안 없음).
 * 지도 가운데 고정된 발바닥에 맞춰 지도를 움직이고, 잰 위치에서 얼마나 떨어졌는지 함께 보여 준다.
 */
export function FootprintPointPicker({ visible, measured, initialPoint, onConfirm, onClose }: FootprintPointPickerProps) {
  return (
    <Modal visible={visible} animationType="slide" statusBarTranslucent navigationBarTranslucent onRequestClose={onClose}>
      {visible ? <PickerContent measured={measured} initialPoint={initialPoint} onConfirm={onConfirm} onClose={onClose} /> : null}
    </Modal>
  );
}

function PickerContent({ measured, initialPoint, onConfirm, onClose }: Omit<FootprintPointPickerProps, "visible">) {
  const [center, setCenter] = useState<FootprintPoint>(initialPoint);
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);
  const [focusRequest, setFocusRequest] = useState<MapFocusRequest | null>(null);
  const distance = distanceMeters(measured, center);

  const [initialCamera] = useState<MapCamera>(() => {
    return { lat: initialPoint.latitude, lng: initialPoint.longitude, level: PICKER_LEVEL };
  });

  const currentLocation = useMemo<MapCurrentLocation>(() => {
    return { lat: measured.latitude, lng: measured.longitude, accuracyMeters: measured.accuracyMeters, showDot: true };
  }, [measured]);

  const followMapCenter = ({ south, west, north, east }: MapRegion) => {
    setCenter({ latitude: (south + north) / 2, longitude: (west + east) / 2 });
  };

  const backToMeasured = () => {
    setFocusRequest((previous) => {
      return { id: (previous?.id ?? 0) + 1, lat: measured.latitude, lng: measured.longitude, level: PICKER_LEVEL };
    });
  };

  return (
    <Screen>
      <ScreenHeader
        title="남길 위치 고르기"
        leading={null}
        trailing={<HeaderIconButton icon={X} accessibilityLabel="위치 고르기 닫기" onPress={onClose} testID="footprint-picker.close" />}
        testID="footprint-picker.header"
      />
      <View className="flex-1 bg-brand-muted" testID="footprint-picker.map">
        {phase === "error" ? (
          <View className="flex-1 items-center justify-center gap-3 px-5">
            <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">
              지도를 불러오지 못했어요. 연결을 확인하고 다시 시도해 주세요.
            </Text>
            <Button
              variant="outline"
              onPress={() => {
                setPhase("loading");
                setAttempt(attempt + 1);
              }}
              testID="footprint-picker.map-retry"
            >
              <Text>다시 시도</Text>
            </Button>
          </View>
        ) : (
          <>
            <KakaoMapView
              key={attempt}
              accessibilityLabel="지도를 움직여 발자취를 남길 곳을 고르는 지도"
              javascriptKey={KAKAO_JAVASCRIPT_KEY}
              initialCamera={initialCamera}
              currentLocation={currentLocation}
              currentLocationStyle={CURRENT_LOCATION_STYLE}
              focusRequest={focusRequest}
              onRegionChange={followMapCenter}
              onReady={() => {
                setPhase("ready");
              }}
              onError={() => {
                setPhase("error");
              }}
            />
            <View pointerEvents="none" className="absolute inset-0 items-center justify-center">
              <PawImage />
            </View>
            {phase === "loading" ? (
              <View accessibilityLabel="지도 불러오는 중" className="absolute inset-0 items-center justify-center gap-2 bg-brand-muted">
                <Spinner tone="brand" accessible={false} />
                <Text className="text-caption text-foreground-secondary">지도를 불러오고 있어요</Text>
              </View>
            ) : null}
          </>
        )}
      </View>
      <BottomCta className="items-stretch">
        <View className="gap-1">
          <Text className="text-label text-foreground" accessibilityLiveRegion="polite">
            {`잰 위치에서 ${formatDistance(distance)} 떨어진 곳`}
          </Text>
          <Text className="text-caption text-foreground-secondary">지도를 움직여 발바닥을 실제로 있는 곳에 맞춰 주세요. 파란 점이 기기가 잰 위치예요.</Text>
        </View>
        <View className="flex-row gap-3">
          <Button size="lg" variant="outline" onPress={backToMeasured} disabled={phase !== "ready"} testID="footprint-picker.reset">
            <Text>잰 위치로</Text>
          </Button>
          <Button
            size="lg"
            className="flex-1"
            disabled={phase !== "ready"}
            onPress={() => {
              onConfirm(center);
            }}
            testID="footprint-picker.confirm"
          >
            <Text>이 위치로 정하기</Text>
          </Button>
        </View>
      </BottomCta>
    </Screen>
  );
}
