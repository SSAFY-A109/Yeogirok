import { api } from "@/api/client";
import {
  toCategories,
  toRegions,
  type CategoriesResponseDto,
  type Category,
  type Region,
  type RegionListResponseDto,
} from "@/features/common/model";

/** API-079 여행 컨셉 카테고리 목록. */
export async function getCategories(signal?: AbortSignal): Promise<Category[]> {
  const { data } = await api.get<CategoriesResponseDto>("/categories", { signal });
  return toCategories(data);
}

/** API-078 추천 지역 계층 목록. `parentCode`가 없으면 시·도, 시·도 code면 그 시·도의 시·군·구다. */
export async function getRegions(parentCode: string | null, signal?: AbortSignal): Promise<Region[]> {
  const params = parentCode === null ? undefined : { parentCode };
  const { data } = await api.get<RegionListResponseDto>("/regions", { params, signal });
  return toRegions(data, parentCode);
}
