import { delay, http } from "msw";

import { placeCatalog } from "../fixtures/place";
import { fail, MOCK_DELAY_MS, ok } from "./respond";

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;

export const placeHandlers = [
  http.get("*/api/v1/places", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const url = new URL(request.url);
    const keyword = url.searchParams.get("keyword")?.trim() ?? "";
    const size = Number(url.searchParams.get("size") ?? DEFAULT_PAGE_SIZE);
    const offset = Number(url.searchParams.get("cursor") ?? 0);

    if (keyword === "") {
      return fail(400, "C001", "keyword는 필수입니다.");
    }

    if (!Number.isInteger(size) || size < 1 || size > MAX_PAGE_SIZE) {
      return fail(400, "C011", "페이지 크기 범위를 벗어났습니다.");
    }

    if (!Number.isInteger(offset) || offset < 0) {
      return fail(400, "C010", "커서가 올바르지 않습니다.");
    }

    const terms = keyword.normalize().toLowerCase().split(/\s+/);

    const matched = placeCatalog.filter(({ name, address }) => {
      const searchableText = `${name.replace(/\s+/g, "")} ${address?.replace(/\s+/g, "") ?? ""}`.normalize().toLowerCase();

      return terms.every((term) => {
        return searchableText.includes(term);
      });
    });

    const items = matched.slice(offset, offset + size);
    const nextOffset = offset + items.length;
    return ok({ items, nextCursor: nextOffset < matched.length ? String(nextOffset) : null });
  }),
];
