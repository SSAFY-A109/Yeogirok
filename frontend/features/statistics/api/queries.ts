import { queryOptions, skipToken, useQuery } from "@tanstack/react-query";

import { getVisitStatistics } from "@/features/statistics/api/statistics.api";
import { isValidVisitMapArea, type VisitMapArea } from "@/features/statistics/model";

/** 조회 영역 좌표를 맞출 소수 자릿수. 약 100m 단위라 조금 움직여도 같은 캐시를 쓴다. */
const AREA_COORDINATE_DIGITS = 3;

/** 방문 분포는 서버가 모아서 집계하므로 1분 동안 다시 받지 않는다(집계 주기는 TBD). */
const VISIT_STATISTICS_STALE_MS = 60_000;

export const statisticsKeys = {
  all: ["statistics"] as const,

  visits: (area: VisitMapArea | null, categoryCode: string | null) => {
    return [...statisticsKeys.all, "visits", area, categoryCode] as const;
  },
};

/**
 * 지도에 보이는 영역을 조회 영역으로 맞춘다. 남서는 내리고 북동은 올려 보이는 곳을 빠짐없이 덮는다.
 * 형식·범위가 맞지 않으면 `null`이다(C017 예방).
 */
export function toVisitQueryArea(region: VisitMapArea): VisitMapArea | null {
  const factor = 10 ** AREA_COORDINATE_DIGITS;

  const area = {
    south: Math.floor(region.south * factor) / factor,
    west: Math.floor(region.west * factor) / factor,
    north: Math.ceil(region.north * factor) / factor,
    east: Math.ceil(region.east * factor) / factor,
    level: region.level,
  };

  return isValidVisitMapArea(area) ? area : null;
}

/**
 * 영역·카테고리별 방문 분포(API-046). 영역이 아직 없으면 요청하지 않는다.
 * 지도를 옮겨 새 영역을 받는 동안에는 이전 결과를 그대로 두고, 카테고리를 바꾸면 다른 집계라 비운다.
 */
export function visitStatisticsOptions(area: VisitMapArea | null, categoryCode: string | null) {
  return queryOptions({
    queryKey: statisticsKeys.visits(area, categoryCode),

    queryFn: area === null ? skipToken : ({ signal }) => {
      return getVisitStatistics({ area, categoryCode }, signal);
    },

    placeholderData: (previousData, previousQuery) => {
      return previousQuery?.queryKey[3] === categoryCode ? previousData : undefined;
    },

    staleTime: VISIT_STATISTICS_STALE_MS,
  });
}

export function useVisitStatistics(area: VisitMapArea | null, categoryCode: string | null) {
  return useQuery(visitStatisticsOptions(area, categoryCode));
}
