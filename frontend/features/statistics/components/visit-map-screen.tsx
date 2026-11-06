import { Footprints, LocateFixed } from "lucide-react-native";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Platform, Pressable, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Button } from "@/components/ui/button";
import { FilterChip } from "@/components/ui/filter-chip";
import { Icon } from "@/components/ui/icon";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { useCategories } from "@/features/common/api/queries";
import { FootprintEntryCard } from "@/features/footprint/components/footprint-entry-card";
import { toVisitQueryArea, useVisitStatistics } from "@/features/statistics/api/queries";
import { getStatisticsErrorMessage } from "@/features/statistics/errors";
import { formatVisitPeriod, type VisitCell, type VisitMapArea } from "@/features/statistics/model";
import { useCurrentLocation, type CurrentLocation } from "@/hooks/use-current-location";
import type { MeasuredPosition } from "@/lib/native/location";
import type { MapCurrentLocation, MapFocusRequest, MapRegion } from "@/lib/native/map";
import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";
import { VisitAreaCard } from "./visit-area-card";
import { VisitDistributionMap } from "./visit-distribution-map";

/** 지도가 멈춘 뒤 이만큼 더 움직이지 않으면 그 영역을 조회한다(규칙 20 디바운스). */
const MAP_SETTLE_MS = 400;
/** 내 위치 버튼을 누르면 이 확대 단계까지 당긴다(카카오 지도 level, 작을수록 가깝다). */
const LOCATE_ME_LEVEL = 5;
/** 상태바와 지도 필터 사이 간격(Figma `Map filters` y=58, 상태바 54). */
const MAP_FILTERS_TOP_GAP = 4;
const NO_CELLS: readonly VisitCell[] = [];
const legendIconColor = getColors("light")["map-footprint"];

/**
 * 지도 탭(Figma W15·W15-A·W15-P). 서버가 격자 칸으로 묶은 방문 분포(API-046)를 발바닥 크기로 보여 주고, 발바닥을 누르면 그 칸 집계를 보여 준다.
 * 멀리 볼수록 서버가 칸을 크게 묶어 보내므로 발바닥이 합쳐진다. 클라이언트에서 따로 묶거나 수를 더하지 않는다(규칙 80).
 * 아래 카드에서 위치 권한을 안내하고(W15-P), 권한이 있으면 지금 있는 곳에 발자취를 남기러 간다(W15 `Bottom card`).
 */
export function VisitMapScreen() {
  const [categoryCode, setCategoryCode] = useState<string | null>(null);
  const [selectedCellId, setSelectedCellId] = useState<string | null>(null);
  const { area, handleRegionChange } = useSettledMapArea();
  const statistics = useVisitStatistics(area, categoryCode);
  const cells = statistics.data?.cells ?? NO_CELLS;
  const categories = useCategories();
  const location = useCurrentLocation();
  const { focusRequest, locateMe } = useLocateMe(location);
  const [isPermissionCardHidden, setIsPermissionCardHidden] = useState(false);
  const isPermissionPromptHidden = location.permission !== "granted" && isPermissionCardHidden;

  const measuredPosition = location.position;

  const currentLocation = useMemo<MapCurrentLocation | null>(() => {
    return measuredPosition === null ? null : {
      lat: measuredPosition.latitude,
      lng: measuredPosition.longitude,
      accuracyMeters: measuredPosition.accuracyMeters,
      showDot: true,
    };
  }, [measuredPosition]);

  const selectedCell = cells.find((cell) => {
    return cell.id === selectedCellId;
  }) ?? null;

  const categoryFilterName = categoryCode === null ? null : categories.data?.find((category) => {
    return category.code === categoryCode;
  })?.name ?? categoryCode;

  const changeCategory = (code: string | null) => {
    setCategoryCode(code);
    setSelectedCellId(null);
  };

  const closeCell = () => {
    setSelectedCellId(null);
  };

  return (
    <View className="flex-1 bg-brand-muted" testID="visit-map.screen">
      <VisitDistributionMap
        cells={cells}
        selectedCellId={selectedCell?.id ?? null}
        currentLocation={currentLocation}
        focusRequest={focusRequest}
        onPressCell={setSelectedCellId}
        onPressMap={closeCell}
        onRegionChange={handleRegionChange}
      />
      <MapFilters
        categoryCode={categoryCode}
        onChangeCategory={changeCategory}
        periodLabel={statistics.data ? formatVisitPeriod(statistics.data.period) : null}
        isFetching={statistics.isFetching}
      />
      <View pointerEvents="box-none" className="absolute bottom-3 left-4 right-4 gap-3">
        {Platform.OS === "web" ? null : (
          <View pointerEvents="box-none" className="items-end">
            <LocateMeButton
              isAvailable={location.permission === "granted"}
              isMeasuring={location.isMeasuring}
              onPress={() => {
                if (location.permission !== "granted") {
                  setIsPermissionCardHidden(false);
                  return;
                }

                void locateMe();
              }}
            />
          </View>
        )}
        {selectedCell && statistics.data ? (
          <VisitAreaCard
            cell={selectedCell}
            statistics={statistics.data}
            categoryFilterName={categoryFilterName}
            onClose={closeCell}
          />
        ) : (
          <>
            <StatisticsStatus
              errorMessage={statistics.isError ? getStatisticsErrorMessage(statistics.error) : null}
              isEmpty={statistics.isSuccess && !statistics.isPlaceholderData && cells.length === 0}
              minCount={statistics.data?.minCount ?? null}
              isFetching={statistics.isFetching}
              onRetry={() => {
                void statistics.refetch();
              }}
            />
            {isPermissionPromptHidden ? null : (
              <FootprintEntryCard
                location={location}
                onLater={() => {
                  setIsPermissionCardHidden(true);
                }}
              />
            )}
          </>
        )}
      </View>
    </View>
  );
}

// --- 내 위치

/**
 * 내 위치 버튼. 이미 잰 위치로 먼저 옮기고, 다시 재서 새 위치로 한 번 더 옮긴다.
 * 지도를 옮기면 그 영역의 분포를 다시 조회한다(디바운스 그대로).
 */
function useLocateMe(location: CurrentLocation) {
  const [focusRequest, setFocusRequest] = useState<MapFocusRequest | null>(null);

  const focusOn = (position: MeasuredPosition) => {
    setFocusRequest((previous) => {
      return { id: (previous?.id ?? 0) + 1, lat: position.latitude, lng: position.longitude, level: LOCATE_ME_LEVEL };
    });
  };

  const locateMe = async () => {
    if (location.position !== null) {
      focusOn(location.position);
    }

    const next = await location.measure();

    if (next !== null) {
      focusOn(next);
    }
  };

  return { focusRequest, locateMe };
}

type LocateMeButtonProps = {
  /** 위치 권한이 있는지. 없으면 흐리게 그리고, 누르면 권한 안내 카드를 다시 연다(Figma W15-P `Locate me`). */
  isAvailable: boolean;
  isMeasuring: boolean;
  onPress: () => void;
};

function LocateMeButton({ isAvailable, isMeasuring, onPress }: LocateMeButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="내 위치로 이동"
      accessibilityHint={isAvailable ? undefined : "위치 권한 안내를 다시 열어요"}
      accessibilityState={{ busy: isMeasuring }}
      className={cn("h-touch w-touch items-center justify-center rounded-full border border-border bg-card active:bg-accent", !isAvailable && "opacity-40")}
      onPress={onPress}
      testID="visit-map.locate-me"
    >
      {isMeasuring ? <Spinner tone="brand" accessible={false} /> : <Icon as={LocateFixed} size={20} className="text-icon" />}
    </Pressable>
  );
}

// --- 지도 영역 디바운스

/** 지도가 멈춘 영역을 잠시 기다렸다가 조회 영역으로 넘긴다. 형식·범위가 맞지 않는 영역은 버리고 이전 영역을 쓴다. */
function useSettledMapArea() {
  const [area, setArea] = useState<VisitMapArea | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (timer.current) {
        clearTimeout(timer.current);
      }
    };
  }, []);

  const handleRegionChange = (region: MapRegion) => {
    if (timer.current) {
      clearTimeout(timer.current);
    }

    timer.current = setTimeout(() => {
      const nextArea = toVisitQueryArea(region);

      if (nextArea) {
        setArea(nextArea);
      }
    }, MAP_SETTLE_MS);
  };

  return { area, handleRegionChange };
}

// --- 지도 위 필터

type MapFiltersProps = {
  categoryCode: string | null;
  onChangeCategory: (code: string | null) => void;
  /** 서버가 집계한 기간("전체 기간" 등). 아직 응답이 없으면 `null`. */
  periodLabel: string | null;
  isFetching: boolean;
};

/**
 * 지도 위 필터(Figma `Map filters`). 카테고리는 서버 기준 데이터(API-079)로 받는다.
 * 기간 선택은 DEC-02 결정 전이라 기간을 보내지 않고, 서버가 집계한 기간(지금은 전체 기간)을 보여 주기만 한다.
 */
function MapFilters({ categoryCode, onChangeCategory, periodLabel, isFetching }: MapFiltersProps) {
  const insets = useSafeAreaInsets();
  const categories = useCategories();

  return (
    <View pointerEvents="box-none" className="absolute left-0 right-0 top-0 gap-2" style={{ paddingTop: insets.top + MAP_FILTERS_TOP_GAP }}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-2 px-4">
        <FilterChip
          selected={categoryCode === null}
          onPress={() => {
            onChangeCategory(null);
          }}
          testID="visit-map.category-all"
        >
          전체
        </FilterChip>
        {categories.data?.map((category) => {
          return (
            <FilterChip
              key={category.code}
              selected={categoryCode === category.code}
              onPress={() => {
                onChangeCategory(category.code);
              }}
              testID={`visit-map.category-${category.code}`}
            >
              {category.name}
            </FilterChip>
          );
        })}
        {categories.isError ? (
          <FilterChip
            onPress={() => {
              void categories.refetch();
            }}
            testID="visit-map.category-retry"
          >
            카테고리 다시 불러오기
          </FilterChip>
        ) : null}
      </ScrollView>
      <View pointerEvents="none" className="flex-row flex-wrap items-center gap-2 px-4">
        <MapPill>
          <Text className="text-label-sm text-foreground">{periodLabel === null ? "남긴 발자취" : `남긴 발자취 ${periodLabel}`}</Text>
        </MapPill>
        <MapPill>
          <Icon as={Footprints} size={14} color={legendIconColor} />
          <Text className="text-micro text-foreground-secondary">클수록 많이 다녀갔어요</Text>
        </MapPill>
        {isFetching ? (
          <MapPill testID="visit-map.fetching">
            <Spinner tone="brand" accessible={false} />
            <Text className="text-micro text-foreground-secondary">분포를 불러오고 있어요</Text>
          </MapPill>
        ) : null}
      </View>
    </View>
  );
}

function MapPill({ children, testID }: { children: ReactNode; testID?: string }) {
  return (
    <View className="h-8 flex-row items-center gap-1 rounded-full border border-border bg-card px-3" testID={testID}>
      {children}
    </View>
  );
}

// --- 빈 상태·오류

type StatisticsStatusProps = {
  errorMessage: string | null;
  isEmpty: boolean;
  /** 서버가 이 수보다 적게 남은 칸을 빼고 보낸다(DEC-07). 1보다 크면 빈 상태에서 함께 알려 준다. */
  minCount: number | null;
  isFetching: boolean;
  onRetry: () => void;
};

/** 지금 영역의 분포를 못 받았거나, 받았는데 다녀간 기록이 없을 때 지도 아래에 띄우는 안내. 지도는 그대로 둔다. */
function StatisticsStatus({ errorMessage, isEmpty, minCount, isFetching, onRetry }: StatisticsStatusProps) {
  if (errorMessage) {
    return (
      <View className="flex-row items-center gap-3 rounded-lg border border-border bg-card px-4 py-3" testID="visit-map.error">
        <Text accessibilityRole="alert" className="flex-1 text-body-sm text-foreground-secondary">
          {errorMessage}
        </Text>
        <Button variant="outline" disabled={isFetching} onPress={onRetry} testID="visit-map.retry">
          <Text>다시 시도</Text>
        </Button>
      </View>
    );
  }

  if (isEmpty) {
    return (
      <View className="gap-1 rounded-lg border border-border bg-card px-4 py-3" testID="visit-map.empty">
        <Text className="text-body-sm text-foreground-secondary">이 근처에는 아직 남겨진 발자취가 없어요. 지도를 옮겨 다른 곳을 둘러보세요.</Text>
        {minCount !== null && minCount > 1 ? (
          <Text className="text-caption text-muted-foreground">{`발자취가 ${minCount}개보다 적은 곳은 지도에 보이지 않아요.`}</Text>
        ) : null}
      </View>
    );
  }

  return null;
}
