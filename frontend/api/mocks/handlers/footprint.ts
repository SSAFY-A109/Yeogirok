import { delay, http } from "msw";

import { GUESTBOOK_LIMIT, guestbookLength, normalizeGuestbookBody } from "../../../features/footprint/model";
import { mockDb } from "../db";
import { findCategory } from "../fixtures/common";
import { mockDateTime, type MockFootprint } from "../fixtures/footprint";
import { fail, MOCK_DELAY_MS, ok, readId, requireMockBearer } from "./respond";

function ownFootprint(value: string | readonly string[] | undefined) {
  const id = readId(value);
  const item = id === null ? undefined : mockDb.footprints.get(id);

  if (!item) {
    return fail(404, "F001", "발자취 없음");
  }

  return item.ownerId === 1 ? item : fail(403, "F002", "작성자 본인이 아님");
}

function publicFields({ ownerId: _ownerId, ...item }: MockFootprint) {
  return item;
}

function validDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && new Date(value).toJSON()?.slice(0, 10) === value;
}

export const footprintHandlers = [
  http.get("*/api/v1/users/me/footprints", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const params = new URL(request.url).searchParams;
    const from = params.get("from");
    const to = params.get("to");
    const bbox = params.get("bbox");
    const category = params.get("category");
    const cursor = Number(params.get("cursor") ?? 0);
    const limit = Number(params.get("limit") ?? 20);
    const bounds = bbox?.split(",").map(Number);

    if (bounds && (bounds.length !== 4 || !bounds.every(Number.isFinite) || bounds[0] < -180 || bounds[2] > 180 || bounds[1] < -90 || bounds[3] > 90 || bounds[0] > bounds[2] || bounds[1] > bounds[3])) {
      return fail(400, "C013", "지도 조회 영역 오류");
    }

    if ((from !== null && !validDate(from)) || (to !== null && !validDate(to)) || (from !== null && to !== null && from > to)) {
      return fail(400, "C012", "날짜 범위 오류");
    }

    if ((category !== null && !findCategory(category)) || !Number.isSafeInteger(cursor) || cursor < 0 || !Number.isSafeInteger(limit) || limit < 1 || limit > 50) {
      return fail(400, "C003", "지원하지 않는 조회 조건");
    }

    const matches = [...mockDb.footprints.values()].filter((item) => {
      const day = item.visitedAt.slice(0, 10);
      return item.ownerId === 1 && (!from || day >= from) && (!to || day <= to)
        && (!category || item.categoryCodes.includes(category))
        && (!bounds || (item.longitude >= bounds[0] && item.latitude >= bounds[1] && item.longitude <= bounds[2] && item.latitude <= bounds[3]));
    }).sort((a, b) => {
      return b.visitedAt.localeCompare(a.visitedAt) || b.id - a.id;
    });

    const items = matches.slice(cursor, cursor + limit).map(({ ownerId: _owner, createdAt: _created, version: _version, ...item }) => {
      return item;
    });

    return ok({ items, nextCursor: cursor + limit < matches.length ? String(cursor + limit) : null });
  }),
  http.get("*/api/v1/footprints/:footprintId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const item = ownFootprint(params.footprintId);
    return "ownerId" in item ? ok(publicFields(item)) : item;
  }),
  http.patch("*/api/v1/footprints/:footprintId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const input = await request.json().catch(() => {
      return null;
    });

    if (!input || typeof input !== "object" || !("version" in input) || !Number.isSafeInteger(input.version)) {
      return fail(400, "C002", "요청 형식 오류");
    }

    if (!("body" in input)) {
      return fail(400, "C001", "본문 필드 누락");
    }

    if (typeof input.body !== "string") {
      return fail(400, "C002", "본문 형식 오류");
    }

    if (guestbookLength(input.body) > GUESTBOOK_LIMIT) {
      return fail(400, "C003", "본문 길이 초과");
    }

    const item = ownFootprint(params.footprintId);

    if (!("ownerId" in item)) {
      return item;
    }

    if (item.version !== input.version) {
      return fail(409, "C004", "버전 불일치");
    }

    item.body = input.body.trim() ? normalizeGuestbookBody(input.body) : null;
    item.version += 1;
    return ok({ id: item.id, body: item.body, updatedAt: mockDateTime(), version: item.version });
  }),
  http.delete("*/api/v1/footprints/:footprintId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const item = ownFootprint(params.footprintId);

    if (!("ownerId" in item)) {
      return item;
    }

    const version = new URL(request.url).searchParams.get("version");

    if (version === null || Number(version) !== item.version) {
      return fail(409, "C004", "버전 불일치");
    }

    mockDb.footprints.delete(item.id);
    return ok({ deleted: true, statisticsStatus: "APPLIED" });
  }),
];
