import type { CategoriesResponseDto, RegionListResponseDto } from "../../../features/common/model";

/** v3 확정 카테고리 8종(2026-11-02 백엔드 협의, Figma 카테고리 표). 서버 기준 데이터의 예시 응답이다. */
export const categoriesFixture: CategoriesResponseDto = {
  categories: [
    { code: "NATURE", name: "자연 힐링" },
    { code: "CULTURE", name: "역사 문화" },
    { code: "SIGHT", name: "랜드마크 거리" },
    { code: "FOOD", name: "맛집" },
    { code: "CAFE", name: "카페" },
    { code: "ACTIVITY", name: "체험 액티비티" },
    { code: "SHOPPING", name: "쇼핑 시장" },
    { code: "EVENT", name: "축제 공연 행사" },
  ],
};

type MockProvince = { code: string; name: string; children: { code: string; name: string }[] };

/** 지역 예시 데이터(API-078 실제 code 일부). 응답은 아래 `regionListFixture`가 한 단계씩 만든다. */
const MOCK_PROVINCES: readonly MockProvince[] = [
  {
    code: "51",
    name: "강원특별자치도",
    children: [
      { code: "51150", name: "강릉시" },
      { code: "51210", name: "속초시" },
      { code: "51830", name: "양양군" },
    ],
  },
  {
    code: "50",
    name: "제주특별자치도",
    children: [
      { code: "50110", name: "제주시" },
      { code: "50130", name: "서귀포시" },
    ],
  },
  {
    code: "26",
    name: "부산광역시",
    children: [
      { code: "26200", name: "영도구" },
      { code: "26350", name: "해운대구" },
    ],
  },
  {
    code: "11",
    name: "서울특별시",
    children: [
      { code: "11110", name: "종로구" },
      { code: "11140", name: "중구" },
    ],
  },
];

/** API-078 응답. `parentCode`가 없으면 시·도, 시·도 code면 그 시·군·구, 시·도가 아니면 `null`(서버는 C003). */
export function regionListFixture(parentCode: string | null): RegionListResponseDto | null {
  if (parentCode === null) {
    return {
      items: MOCK_PROVINCES.map((province) => {
        return { code: province.code, name: province.name, level: "PROVINCE", parentCode: null, hasChildren: province.children.length > 0 };
      }),
    };
  }

  const province = MOCK_PROVINCES.find((item) => {
    return item.code === parentCode;
  });

  if (province === undefined) {
    return null;
  }

  return {
    items: province.children.map((district) => {
      return { code: district.code, name: district.name, level: "DISTRICT", parentCode: province.code, hasChildren: false };
    }),
  };
}

export function findCategory(code: string) {
  return categoriesFixture.categories.find((category) => {
    return category.code === code;
  }) ?? null;
}
