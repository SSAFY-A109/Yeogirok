import { router } from "expo-router";
import { View } from "react-native";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { LocationPermissionCard } from "@/features/footprint/components/location-permission-card";
import { PreciseLocationNotice } from "@/features/footprint/components/precise-location-notice";
import { getLocationFailureMessage } from "@/features/footprint/errors";
import { formatLocationEvidence } from "@/features/footprint/model";
import type { CurrentLocation } from "@/hooks/use-current-location";

type FootprintEntryCardProps = {
  location: CurrentLocation;
  /** 권한 안내에서 "나중에"를 눌렀을 때. 카드를 숨기는 일은 지도 화면이 한다. */
  onLater: () => void;
};

/**
 * 지도 탭 아래 카드(FPT-01). 위치 권한이 없으면 사전 안내(Figma W15-P), 있으면 지금 있는 곳과
 * "여기에 발자취 남기기"(Figma W15 `Bottom card`)를 보여 준다. 권한 상태를 읽는 동안은 그리지 않는다.
 */
export function FootprintEntryCard({ location, onLater }: FootprintEntryCardProps) {
  if (location.permission === null) {
    return null;
  }

  if (location.permission !== "granted") {
    return (
      <LocationPermissionCard
        permission={location.permission}
        isRequesting={location.isRequestingPermission}
        onRequest={() => {
          void location.requestPermission();
        }}
        onLater={onLater}
        testID="visit-map.location-permission"
      />
    );
  }

  if (location.position === null && location.failure !== null && !location.isMeasuring) {
    return (
      <View className="gap-4 rounded-lg border border-border bg-card p-4" testID="visit-map.location-error">
        <View className="gap-1">
          <Text accessibilityRole="header" className="text-h3 text-foreground">지금 계신 곳을 확인하지 못했어요</Text>
          <Text accessibilityRole="alert" className="text-caption text-foreground-secondary">{getLocationFailureMessage(location.failure)}</Text>
        </View>
        <Button
          size="lg"
          variant="outline"
          className="w-full"
          onPress={() => {
            void location.measure();
          }}
          testID="visit-map.location-retry"
        >
          <Text>다시 확인</Text>
        </Button>
      </View>
    );
  }

  return <CurrentPlaceCard location={location} />;
}

// --- 지금 있는 곳

function toPlaceTitle({ position, placeLabel }: CurrentLocation): string {
  if (position === null) {
    return "지금 계신 곳을 확인하고 있어요";
  }

  return placeLabel === null ? "지금 계신 곳" : `지금 계신 곳 ${placeLabel} 부근`;
}

/** 잰 위치의 근처 주소·정확도·측정 시각과 발자취 남기기 버튼(Figma W15 `Bottom card`). 주소는 기기 지오코더의 참고 값이라 "부근"으로 적는다. */
function CurrentPlaceCard({ location }: { location: CurrentLocation }) {
  const { position, isMeasuring, failure } = location;

  return (
    <View className="gap-4 rounded-lg border border-border bg-card p-4" testID="visit-map.current-place">
      <View className="gap-1">
        <Text accessibilityRole="header" className="text-h3 text-foreground" numberOfLines={2}>{toPlaceTitle(location)}</Text>
        <Text className="text-caption text-foreground-secondary">{position === null ? "잠시만 기다려 주세요" : formatLocationEvidence(position)}</Text>
        {position !== null && failure !== null ? (
          <Text accessibilityRole="alert" className="text-caption text-destructive">{getLocationFailureMessage(failure)}</Text>
        ) : null}
      </View>
      <PreciseLocationNotice location={location} testID="visit-map.precise-location" />
      <Button
        size="lg"
        className="w-full"
        disabled={position === null}
        loading={position === null && isMeasuring}
        loadingLabel="위치 확인 중"
        onPress={() => {
          router.push("/footprints/new");
        }}
        testID="visit-map.leave-footprint"
      >
        <Text>여기에 발자취 남기기</Text>
      </Button>
    </View>
  );
}
