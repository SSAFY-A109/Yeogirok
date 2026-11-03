import { api } from "@/api/client";
import {
  toCategories,
  toRegions,
  type CategoriesResponseDto,
  type Category,
  type Region,
  type RegionsResponseDto,
} from "@/features/common/model";

/** API-079 여행 컨셉 카테고리 목록. */
export async function getCategories(signal?: AbortSignal): Promise<Category[]> {
  const { data } = await api.get<CategoriesResponseDto>("/categories", { signal });
  return toCategories(data);
}

/** API-078 관심 지역 목록(시/도와 그 아래 시/군/구). */
export async function getRegions(signal?: AbortSignal): Promise<Region[]> {
  const { data } = await api.get<RegionsResponseDto>("/regions", { signal });
  return toRegions(data);
}
