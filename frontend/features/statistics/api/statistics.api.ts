import { api } from "@/api/client";
import {
  toVisitStatistics,
  type VisitMapArea,
  type VisitStatistics,
  type VisitStatisticsResponseDto,
} from "@/features/statistics/model";

export type VisitStatisticsInput = { area: VisitMapArea; categoryCode: string | null };

/** API-046 카테고리별 방문 분포도. 카테고리가 없으면 전체를 묻는다. 쿼리 이름은 명세 미확정이라 가정이다(TBD). */
export async function getVisitStatistics({ area, categoryCode }: VisitStatisticsInput, signal?: AbortSignal): Promise<VisitStatistics> {
  const { data } = await api.get<VisitStatisticsResponseDto>("/visit-statistics", {
    params: {
      swLat: area.south,
      swLng: area.west,
      neLat: area.north,
      neLng: area.east,
      level: area.level,
      category: categoryCode ?? undefined,
    },
    signal,
  });
  return toVisitStatistics(data);
}
