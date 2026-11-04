import type { CategoriesResponseDto, RegionsResponseDto } from "../../../features/common/model";

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

export const regionsFixture: RegionsResponseDto = {
  regions: [
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
  ],
};

export function findCategory(code: string) {
  return categoriesFixture.categories.find((category) => {
    return category.code === code;
  }) ?? null;
}
