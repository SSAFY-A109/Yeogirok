import { delay, http } from "msw";

import { ApiError } from "../../../api/errors";
import { normalizeTipBody, validateTipType, validateTipUploads, type TipOperationResponseDto, type UpdateTipRequestDto } from "../../../features/tip/model";
import { mockDb } from "../db";
import { mockDateTime } from "../fixtures/footprint";
import type { MockTip } from "../fixtures/tip";
import { fail, MOCK_DELAY_MS, ok, readId, requireMockBearer } from "./respond";

function settleTip(tip: MockTip) {
  if (tip.pending && Date.now() >= tip.pending.readyAt) {
    tip.detail = tip.pending.candidate;
    tip.lastStatus = {
      ...tip.lastStatus,
      status: tip.detail.status,
      operationStatus: "SUCCEEDED",
      version: tip.detail.version,
      steps: { media: tip.lastStatus.steps.media === "QUEUED" ? "SUCCEEDED" : "SKIPPED", spatial: "SKIPPED", content: "SUCCEEDED" },
    };
    tip.pending = null;
  }
}

function ownTip(value: string | readonly string[] | undefined, includeDeleted = false) {
  const id = readId(value);
  const tip = id === null ? undefined : mockDb.myTips.get(id);

  if (!tip || (!includeDeleted && tip.detail.status === "DELETED")) {
    return fail(404, "T012", "팁 없음");
  }

  if (tip.ownerId !== 1) {
    return fail(403, "T002", "작성자 본인이 아님");
  }

  settleTip(tip);
  return tip;
}

export const myTipHandlers = [
  http.get("*/api/v1/users/me/tips", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const params = new URL(request.url).searchParams;
    const status = params.get("status");
    const cursor = Number(params.get("cursor") ?? 0);
    const limit = Number(params.get("limit") ?? 20);

    if ((status !== null && !["REGISTERING", "PUBLISHED", "FAILED", "DELETED"].includes(status)) || !Number.isSafeInteger(cursor) || cursor < 0 || !Number.isSafeInteger(limit) || limit < 1) {
      return fail(400, "C002", "요청 형식 오류");
    }

    const items = [...mockDb.myTips.values()].filter((tip) => {
      settleTip(tip);
      return tip.ownerId === 1 && tip.detail.status !== "DELETED" && (!status || status === tip.detail.status);
    }).sort((a, b) => {
      return b.detail.createdAt.localeCompare(a.detail.createdAt) || b.detail.tipId - a.detail.tipId;
    });

    return ok({ items: items.slice(cursor, cursor + limit).map(({ detail }) => {
      return { id: detail.tipId, category: detail.category, status: detail.status, createdAt: detail.createdAt, updatedAt: detail.updatedAt };
    }),

    nextCursor: cursor + limit < items.length ? String(cursor + limit) : null });
  }),
  http.get("*/api/v1/users/me/tips/:tipId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const tip = ownTip(params.tipId);
    return "detail" in tip ? ok(tip.detail) : tip;
  }),
  http.get("*/api/v1/tips/:tipId/status", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const tip = ownTip(params.tipId, true);
    return "detail" in tip ? ok(tip.lastStatus) : tip;
  }),
  http.patch<{ tipId: string }, UpdateTipRequestDto>("*/api/v1/tips/:tipId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const key = request.headers.get("Idempotency-Key");

    const input = await request.json().catch(() => {
      return null;
    });

    if (!key) {
      return fail(400, "C001", "Idempotency-Key 필요");
    }

    if (!input || !Number.isSafeInteger(input.version)) {
      return fail(400, "C002", "요청 형식 오류");
    }

    const tip = ownTip(params.tipId);

    if (!("detail" in tip)) {
      return tip;
    }

    const { detail } = tip;
    const signature = JSON.stringify(input);
    const previous = mockDb.tipUpdates.get(key);

    if (previous) {
      return previous.tipId === detail.tipId && previous.request === signature ? ok(previous.result, 202) : fail(409, "C005", "멱등키 충돌");
    }

    if (detail.version !== input.version) {
      return fail(409, "C004", "버전 불일치");
    }

    if (tip.pending) {
      return fail(409, "T010", "수정 작업 진행 중");
    }

    if (input.category === undefined && input.body === undefined && input.uploadIds === undefined) {
      return fail(400, "C003", "변경할 내용 없음");
    }

    let category: string;
    let body: string;
    let uploadIds: string[] | undefined;

    try {
      category = input.category === undefined ? detail.category : validateTipType(input.category);
      body = normalizeTipBody(input.body === undefined ? detail.body : input.body);
      uploadIds = validateTipUploads(input.uploadIds);
    } catch (error) {
      return error instanceof ApiError ? fail(error.status, error.code, "요청 검증 실패") : fail(400, "C002", "요청 형식 오류");
    }

    let photos = detail.photos;

    if (uploadIds) {
      const uploadId = uploadIds[0];
      photos = [];

      if (uploadId) {
        const upload = mockDb.uploads.get(uploadId);

        if (!upload) {
          return fail(404, "M004", "파일 없음");
        }

        if (!upload.result) {
          return fail(422, "M006", "업로드 미완료");
        }

        if (upload.request.purpose !== "TIP_PHOTO") {
          return fail(422, "M007", "용도 불일치");
        }

        const imageId = mockDb.nextTipImageId++;
        photos = [{ imageId, url: `https://uploads.yeogirok.test/photos/${imageId}`, expiresAt: mockDateTime(Date.now() + 3_600_000) }];
      }
    }

    const jobId = `550e8400-e29b-41d4-a717-${String(mockDb.nextTipJobId++).padStart(12, "0")}`;
    const status = detail.status === "PUBLISHED" ? "PUBLISHED" : "REGISTERING";
    const result: TipOperationResponseDto = { tipId: detail.tipId, jobId, status, operation: "UPDATE", operationStatus: "QUEUED", version: detail.version };
    const readyAt = Date.now() + 1_500;
    tip.pending = { readyAt, candidate: { ...detail, category, body, photos, status: "PUBLISHED", version: detail.version + 1, publishedAt: detail.publishedAt ?? mockDateTime(readyAt), updatedAt: mockDateTime(readyAt) } };
    tip.lastStatus = { ...result, steps: { media: uploadIds?.length ? "QUEUED" : "SKIPPED", spatial: "SKIPPED", content: "QUEUED" }, error: null, retryable: false };
    mockDb.tipUpdates.set(key, { tipId: detail.tipId, request: signature, result });
    return ok(result, 202);
  }),
  http.delete("*/api/v1/tips/:tipId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const tip = ownTip(params.tipId, true);

    if (!("detail" in tip)) {
      return tip;
    }

    const result = { tipId: tip.detail.tipId, status: "DELETED" };

    if (tip.detail.status === "DELETED") {
      return ok(result);
    }

    const version = new URL(request.url).searchParams.get("version");

    if (version === null || Number(version) !== tip.detail.version) {
      return fail(409, "C004", "버전 불일치");
    }

    tip.pending = null;
    tip.detail = { ...tip.detail, status: "DELETED", version: tip.detail.version + 1, updatedAt: mockDateTime() };
    tip.lastStatus = { ...tip.lastStatus, status: "DELETED", version: tip.detail.version, operationStatus: null, retryable: false };
    return ok(result);
  }),
];
