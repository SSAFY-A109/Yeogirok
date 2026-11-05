import { Footprints } from "lucide-react-native";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Button } from "@/components/ui/button";
import { FilterChip } from "@/components/ui/filter-chip";
import { Icon } from "@/components/ui/icon";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import { useCategories } from "@/features/common/api/queries";
import { toVisitQueryArea, useVisitStatistics } from "@/features/statistics/api/queries";
import { getStatisticsErrorMessage } from "@/features/statistics/errors";
import type { VisitArea, VisitMapArea } from "@/features/statistics/model";
import type { MapRegion } from "@/lib/native/map";
import { getColors } from "@/lib/theme";
import { VisitAreaCard } from "./visit-area-card";
import { VisitDistributionMap } from "./visit-distribution-map";

/** 지도가 멈춘 뒤 이만큼 더 움직이지 않으면 그 영역을 조회한다(규칙 20 디바운스). */
const MAP_SETTLE_MS = 400;
/** 상태바와 지도 필터 사이 간격(Figma `Map filters` y=58, 상태바 54). */
const MAP_FILTERS_TOP_GAP = 4;
const NO_AREAS: readonly VisitArea[] = [];
const legendIconColor = getColors("light")["map-footprint"];

/** 지도 탭(Figma W15·W15-A). 서버가 집계한 방문 분포를 발바닥 크기로 보여 주고, 발바닥을 누르면 그 일대 집계를 보여 준다. */
export function VisitMapScreen() {
  const [categoryCode, setCategoryCode] = useState<string | null>(null);
  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(null);
  const { area, handleRegionChange } = useSettledMapArea();
  const statistics = useVisitStatistics(area, categoryCode);
  const areas = statistics.data?.areas ?? NO_AREAS;

  const selectedArea = areas.find((visitArea) => {
    return visitArea.id === selectedAreaId;
  }) ?? null;

  const changeCategory = (code: string | null) => {
    setCategoryCode(code);
    setSelectedAreaId(null);
  };

  const closeArea = () => {
    setSelectedAreaId(null);
  };

  return (
    <View className="flex-1 bg-brand-muted" testID="visit-map.screen">
      <VisitDistributionMap
        areas={areas}
        selectedAreaId={selectedArea?.id ?? null}
        onPressArea={setSelectedAreaId}
        onPressMap={closeArea}
        onRegionChange={handleRegionChange}
      />
      <MapFilters
        categoryCode={categoryCode}
        onChangeCategory={changeCategory}
        periodDays={statistics.data?.periodDays ?? null}
        isFetching={statistics.isFetching}
      />
      <View pointerEvents="box-none" className="absolute bottom-3 left-4 right-4">
        {selectedArea && statistics.data ? (
          <VisitAreaCard area={selectedArea} periodDays={statistics.data.periodDays} onClose={closeArea} />
        ) : (
          <StatisticsStatus
            errorMessage={statistics.isError ? getStatisticsErrorMessage(statistics.error) : null}
            isEmpty={statistics.isSuccess && !statistics.isPlaceholderData && areas.length === 0}
            isFetching={statistics.isFetching}
            onRetry={() => {
              void statistics.refetch();
            }}
          />
        )}
      </View>
    </View>
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
  periodDays: number | null;
  isFetching: boolean;
};

/** 지도 위 필터(Figma `Map filters`). 카테고리는 서버 기준 데이터(API-079)로 받는다. 기간은 DEC-02 결정 전이라 서버 값을 보여 주기만 한다. */
function MapFilters({ categoryCode, onChangeCategory, periodDays, isFetching }: MapFiltersProps) {
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
          <Text className="text-label-sm text-foreground">{periodDays === null ? "다녀간 사람" : `다녀간 사람 최근 ${periodDays}일`}</Text>
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
  isFetching: boolean;
  onRetry: () => void;
};

/** 지금 영역의 분포를 못 받았거나, 받았는데 다녀간 기록이 없을 때 지도 아래에 띄우는 안내. 지도는 그대로 둔다. */
function StatisticsStatus({ errorMessage, isEmpty, isFetching, onRetry }: StatisticsStatusProps) {
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
      <View className="rounded-lg border border-border bg-card px-4 py-3" testID="visit-map.empty">
        <Text className="text-body-sm text-foreground-secondary">이 근처에는 아직 남겨진 발자취가 없어요. 지도를 옮겨 다른 곳을 둘러보세요.</Text>
      </View>
    );
  }

  return null;
}
