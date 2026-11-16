import { ContractMismatchError } from "../../lib/contract";

// API-079 여행 컨셉 카테고리: Notion 명세 확정(2026-11-11, S15P31A109-202). `{ categories[{ code, name }] }`, 배열 순서가 표시 순서다.
// API-078 추천 지역 계층 목록: 백엔드 구현 기준(backend/dev #144, 2026-11-16 대조). 한 번에 한 단계만 준다.
// `parentCode` 없이 부르면 시·도, 시·도 code를 주면 그 시·도의 시·군·구를 화면 순서대로 준다.

export type CategoryDto = { code: string; name: string };
export type CategoriesResponseDto = { categories: CategoryDto[] };

export const REGION_LEVELS = ["PROVINCE", "DISTRICT"] as const;
export type RegionLevelDto = (typeof REGION_LEVELS)[number];

export type RegionItemDto = { code: string; name: string; level: RegionLevelDto; parentCode: string | null; hasChildren: boolean };
export type RegionListResponseDto = { items: RegionItemDto[] };

export type Category = { code: string; name: string };
/** 시·도나 시·군·구 하나. */
export type Region = { code: string; name: string };

/** 시·도 code 자리수. 시·군·구 code(5자리)의 앞 두 자리가 그 시·도 code다(API-078 데이터 2026-11-16 확인: 시·도 16·시·군·구 256 모두 일치). */
const PROVINCE_CODE_LENGTH = 2;

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

/**
 * API-078 응답을 한 단계 지역 목록으로 바꾼다. `parentCode`가 없으면 모두 시·도여야 하고,
 * 있으면 모두 그 시·도 아래 시·군·구여야 한다. 다르면 계약 불일치다.
 */
export function toRegions(dto: RegionListResponseDto, parentCode: string | null): Region[] {
  if (!Array.isArray(dto.items)) {
    throw new ContractMismatchError("regions.items");
  }

  const expectedLevel: RegionLevelDto = parentCode === null ? "PROVINCE" : "DISTRICT";

  return dto.items.map((item) => {
    if (item.level !== expectedLevel || (item.parentCode ?? null) !== parentCode) {
      throw new ContractMismatchError("regions.items.level");
    }

    return assertCodeName(item, "regions.items");
  });
}

/** 시·군·구 code가 속한 시·도 code. 시·도 code를 넣으면 그대로 돌려준다. */
export function toProvinceCode(regionCode: string): string {
  return regionCode.slice(0, PROVINCE_CODE_LENGTH);
}
