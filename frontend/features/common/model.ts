import { ContractMismatchError } from "../../lib/contract";

// 가정(TBD): API-078 관심 지역, API-079 여행 컨셉 카테고리의 응답 상세는 명세 미확정이다.
// W10 AI 추천 조건 화면의 "시/도 → 시/군/구" 2단 선택에 맞춰 지역을 2단 구조로 가정했다.

export type CategoryDto = { code: string; name: string };
export type CategoriesResponseDto = { categories: CategoryDto[] };

export type RegionDto = { code: string; name: string; children: { code: string; name: string }[] };
export type RegionsResponseDto = { regions: RegionDto[] };

export type Category = { code: string; name: string };
export type Region = { code: string; name: string; districts: { code: string; name: string }[] };

function assertCodeName(value: { code: unknown; name: unknown }, field: string): { code: string; name: string } {
  if (typeof value.code !== "string" || typeof value.name !== "string") {
    throw new ContractMismatchError(field);
  }

  return { code: value.code, name: value.name };
}

export function toCategories(dto: CategoriesResponseDto): Category[] {
  return dto.categories.map((category) => {
    return assertCodeName(category, "categories");
  });
}

export function toRegions(dto: RegionsResponseDto): Region[] {
  return dto.regions.map((region) => {
    return {
      ...assertCodeName(region, "regions"),

      districts: region.children.map((district) => {
        return assertCodeName(district, "regions.children");
      }),
    };
  });
}
