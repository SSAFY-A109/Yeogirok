import { delay, http } from "msw";

import { categoriesFixture, regionListFixture } from "../fixtures/common";
import { fail, MOCK_DELAY_MS, ok } from "./respond";

export const commonHandlers = [
  http.get("*/api/v1/categories", async () => {
    await delay(MOCK_DELAY_MS);
    return ok(categoriesFixture);
  }),

  http.get("*/api/v1/regions", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const regions = regionListFixture(new URL(request.url).searchParams.get("parentCode"));
    return regions === null ? fail(400, "C003", "parentCode: 시·도 지역 코드여야 합니다.") : ok(regions);
  }),
];
