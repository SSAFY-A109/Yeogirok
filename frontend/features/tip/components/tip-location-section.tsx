import { MapPin, RotateCcw } from "lucide-react-native";
import { useMemo, useState, type ReactNode } from "react";
import { Pressable, View } from "react-native";

import { Button } from "@/components/ui/button";
import { DesignAsset } from "@/components/ui/design-asset";
import { Icon } from "@/components/ui/icon";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import {
  canShowLocationMap,
  CURRENT_LOCATION_STYLE,
  isSamePoint,
  KAKAO_JAVASCRIPT_KEY,
  toFocusId,
} from "@/features/footprint/components/footprint-location-map";
import { FootprintPointPicker, type PointPickerCopy } from "@/features/footprint/components/footprint-point-picker";
import { FOOTPRINT_ADJUST_RADIUS_METERS, isWithinAdjustRadius } from "@/features/footprint/model";
import { KakaoMapView, placePinImage, type MapCamera, type MapCurrentLocation, type MapFocusRequest, type MapMarker } from "@/lib/native/map";
import { cn } from "@/lib/utils";

import type { LocationEvidenceDto, TipPoint } from "../model";

/** 쓰기 화면 지도 높이. 발자취 남기기 지도(Figma W16 `Location map`)와 같다. */
const MAP_HEIGHT = 170;
/** 정확도 원과 핀이 함께 보이는 확대 단계. */
const MAP_LEVEL = 3;

const PIN_SOURCE = require("@/assets/images/map/place-pin.png");

const PICKER_COPY: PointPickerCopy = {
  mapLabel: "지도를 움직여 팁을 둘 곳을 고르는 지도",
  guide: `지도를 움직여 핀을 팁을 남길 곳에 맞춰 주세요. 파란 점이 기기가 잰 위치이고, 원 안(${FOOTPRINT_ADJUST_RADIUS_METERS}m)에서만 정할 수 있어요.`,
  outOfRange: `잰 위치에서 ${FOOTPRINT_ADJUST_RADIUS_METERS}m 넘게 벗어났어요. 핀을 원 안으로 옮겨 주세요.`,
};

type MapPhase = "loading" | "ready" | "error";

/** 쓰기 화면이 아는 현재 위치. 자리를 놓으면 다시 잰다. */
export type TipLocationState = {
  measured: LocationEvidenceDto | null;
  measuring: boolean;
  error: string | null;
};

type TipLocationSectionProps = {
  location: TipLocationState;
  /** 지도에서 직접 고친 위치. 없으면 잰 위치에 팁을 둔다. */
  chosen: TipPoint | null;
  disabled: boolean;
  onRemeasure: () => void;
  onChoose: (point: TipPoint) => void;
  onReset: () => void;
};

/**
 * 팁을 둘 위치 지도(2026-11-16 사용자 요청, 시안 없음). 잰 위치에 정확도 원을, 팁을 둘 곳에 핀을 그린다.
 * "위치 수정"을 누르면 발자취 남기기와 같은 화면에서 잰 위치로부터 `FOOTPRINT_ADJUST_RADIUS_METERS` 안으로 핀을 옮긴다.
 * 지도를 못 띄워도 잰 위치로 등록할 수 있으므로 이 영역만 안내로 바꾼다.
 */
export function TipLocationSection({ location, chosen, disabled, onRemeasure, onChoose, onReset }: TipLocationSectionProps) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const { measured } = location;
  const outOfRange = measured !== null && chosen !== null && !isWithinAdjustRadius(measured, chosen);

  return (
    <View className="gap-3" testID="ar-tip.location">
      {measured === null ? (
        <MapPlaceholder>
          {location.measuring ? (
            <>
              <Spinner tone="brand" accessible={false} />
              <Text accessibilityLiveRegion="polite" className="text-caption text-foreground-secondary">현재 위치를 확인하고 있어요</Text>
            </>
          ) : (
            <>
              <Text accessibilityRole="alert" className="text-center text-caption text-foreground-secondary">{location.error ?? "현재 위치를 확인하지 못했어요"}</Text>
              <Button variant="outline" className="h-9 px-3" disabled={disabled} onPress={onRemeasure} testID="ar-tip.location-retry">
                <Text>다시 확인</Text>
              </Button>
            </>
          )}
        </MapPlaceholder>
      ) : (
        <TipLocationMap measured={measured} point={chosen ?? measured} />
      )}
      <View className="flex-row items-center gap-2">
        <Icon as={MapPin} size={18} className="text-icon" />
        <View className="flex-1 gap-0.5">
          <Text className="text-label text-foreground">{chosen === null ? "현재 위치에 팁을 둬요" : "지도에서 고친 위치에 팁을 둬요"}</Text>
          {measured !== null && chosen === null ? (
            <Text className="text-caption text-foreground-secondary tabular-nums">{`정확도 약 ${Math.round(measured.accuracyMeters)}m`}</Text>
          ) : null}
          {outOfRange ? (
            <Text accessibilityRole="alert" className="text-caption text-destructive" testID="ar-tip.location-out-of-range">
              {`지금 위치에서 ${FOOTPRINT_ADJUST_RADIUS_METERS}m 넘게 떨어졌어요. 위치를 다시 수정하거나 되돌려 주세요.`}
            </Text>
          ) : null}
        </View>
        {measured !== null ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="현재 위치 다시 확인"
            accessibilityState={{ disabled: disabled || location.measuring, busy: location.measuring }}
            disabled={disabled || location.measuring}
            onPress={onRemeasure}
            className="min-h-touch justify-center pl-2"
            testID="ar-tip.location-recheck"
          >
            <Text className={cn("text-label", disabled || location.measuring ? "text-muted-foreground" : "text-primary")}>{location.measuring ? "확인 중" : "다시 확인"}</Text>
          </Pressable>
        ) : null}
      </View>
      {canShowLocationMap ? (
        <View className="flex-row gap-2">
          <Button
            variant="outline"
            disabled={disabled || measured === null}
            onPress={() => {
              setPickerOpen(true);
            }}
            testID="ar-tip.pick-location"
          >
            <Text>위치 수정</Text>
          </Button>
          {chosen !== null ? (
            <Button variant="outline" className="w-touch px-0" accessibilityLabel="잰 위치로 되돌리기" disabled={disabled} onPress={onReset} testID="ar-tip.reset-location">
              <Icon as={RotateCcw} size={20} className={disabled ? "text-disabled-foreground" : "text-icon-active"} />
            </Button>
          ) : null}
        </View>
      ) : null}
      {measured !== null ? (
        <FootprintPointPicker
          visible={pickerOpen}
          measured={measured}
          initialPoint={chosen === null || outOfRange ? measured : chosen}
          marker={<PinMarker />}
          copy={PICKER_COPY}
          testID="ar-tip.picker"

          onConfirm={(point) => {
            onChoose(point);
            setPickerOpen(false);
          }}

          onClose={() => {
            setPickerOpen(false);
          }}
        />
      ) : null}
    </View>
  );
}

function TipLocationMap({ measured, point }: { measured: LocationEvidenceDto; point: TipPoint }) {
  const [phase, setPhase] = useState<MapPhase>("loading");
  const [attempt, setAttempt] = useState(0);
  const isChosen = !isSamePoint(measured, point);

  const [initialCamera] = useState<MapCamera>(() => {
    return { lat: point.latitude, lng: point.longitude, level: MAP_LEVEL };
  });

  const markers = useMemo<MapMarker[]>(() => {
    return [{ id: "tip", lat: point.latitude, lng: point.longitude }];
  }, [point]);

  const currentLocation = useMemo<MapCurrentLocation>(() => {
    return { lat: measured.latitude, lng: measured.longitude, accuracyMeters: measured.accuracyMeters, showDot: isChosen };
  }, [measured, isChosen]);

  const focusRequest = useMemo<MapFocusRequest>(() => {
    return { id: toFocusId(point), lat: point.latitude, lng: point.longitude, level: MAP_LEVEL };
  }, [point]);

  if (!canShowLocationMap) {
    return (
      <MapPlaceholder>
        <PinImage />
        <Text className="text-caption text-foreground-secondary">지도는 Android·iOS 앱에서 볼 수 있어요</Text>
      </MapPlaceholder>
    );
  }

  if (phase === "error") {
    return (
      <MapPlaceholder>
        <Text className="text-center text-caption text-foreground-secondary">지도를 불러오지 못했어요. 팁은 그대로 등록할 수 있어요.</Text>
        <Button
          variant="outline"
          className="h-9 px-3"
          accessibilityLabel="지도 다시 불러오기"

          onPress={() => {
            setPhase("loading");
            setAttempt(attempt + 1);
          }}

          testID="ar-tip.map-retry"
        >
          <Text>다시 시도</Text>
        </Button>
      </MapPlaceholder>
    );
  }

  return (
    <View className="w-full overflow-hidden rounded-md bg-brand-muted" style={{ height: MAP_HEIGHT }} testID="ar-tip.map">
      <KakaoMapView
        key={attempt}
        accessibilityLabel="팁을 둘 위치 지도"
        javascriptKey={KAKAO_JAVASCRIPT_KEY}
        initialCamera={initialCamera}
        markers={markers}
        markerImage={placePinImage}
        currentLocation={currentLocation}
        currentLocationStyle={CURRENT_LOCATION_STYLE}
        focusRequest={focusRequest}

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

function MapPlaceholder({ children }: { children: ReactNode }) {
  return (
    <View className="w-full items-center justify-center gap-2 rounded-md bg-brand-muted px-5" style={{ height: MAP_HEIGHT }} testID="ar-tip.map-placeholder">
      {children}
    </View>
  );
}

function PinImage() {
  return <DesignAsset source={PIN_SOURCE} contentFit="contain" style={{ width: placePinImage.width, height: placePinImage.height }} accessible={false} />;
}

/** 위치 수정 화면 가운데 핀. 핀 끝이 지도 가운데에 오도록 핀 높이만큼 아래를 비운다. */
function PinMarker() {
  return (
    <View style={{ paddingBottom: placePinImage.height }}>
      <PinImage />
    </View>
  );
}
