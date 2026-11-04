import { delay, http } from "msw";

import { categoriesFixture, regionsFixture } from "../fixtures/common";
import { MOCK_DELAY_MS, ok } from "./respond";

export const commonHandlers = [
  http.get("*/api/v1/categories", async () => {
    await delay(MOCK_DELAY_MS);
    return ok(categoriesFixture);
  }),

  http.get("*/api/v1/regions", async () => {
    await delay(MOCK_DELAY_MS);
    return ok(regionsFixture);
  }),
];
