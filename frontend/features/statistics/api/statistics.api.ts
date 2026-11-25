import { api } from "@/api/client";
import {
  toVisitStatistics,
  toVisitStatisticsParams,
  type VisitMapArea,
  type VisitStatistics,
  type VisitStatisticsResponseDto,
} from "@/features/statistics/model";

export type VisitStatisticsInput = { area: VisitMapArea; categoryCode: string | null };

/** API-046 카테고리별 방문 분포. 보이는 영역과 확대 정도로 묻고, 서버가 묶은 격자 칸을 받는다. 기간(DEC-02)은 보내지 않는다(전체 기간). */
export async function getVisitStatistics({ area, categoryCode }: VisitStatisticsInput, signal?: AbortSignal): Promise<VisitStatistics> {
  const { data } = await api.get<VisitStatisticsResponseDto>("/visit-statistics", {
    params: toVisitStatisticsParams(area, categoryCode),
    signal,
  });
  return toVisitStatistics(data);
}
