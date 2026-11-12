import { http } from "msw";

import { ApiError } from "../../errors";
import {
  normalizeTipBody, TIP_INTERACTION_RADIUS_METERS, validateTipLocation, validateTipSpatialFields, validateTipUploads, validateTipType,
  type CreateTipRequestDto, type LocationEvidenceDto, type RetryTipRequestDto, type UpdateTipRequestDto,
} from "../../../features/tip/model";
import { mockDb } from "../db";
import { tipFixture, type MockTip } from "../fixtures/tip";
import { formatServerDateTime } from "../../../lib/date";
import { fail, ok, readId } from "./respond";

function distance(location: LocationEvidenceDto, tip: MockTip): number {
  const rad = Math.PI / 180;
  const a = Math.sin((location.latitude - tip.detail.location.latitude) * rad / 2) ** 2 +
    Math.cos(location.latitude * rad) * Math.cos(tip.detail.location.latitude * rad) *
    Math.sin((location.longitude - tip.detail.location.longitude) * rad / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function uploadError(ids: string[] | undefined): Response | null {
  validateTipUploads(ids);

  for (const id of ids ?? []) {
    const upload = mockDb.uploads.get(id);

    if (!upload) {
      return fail(404, "M004", "파일 없음");
    }

    if (upload.request.purpose !== "TIP_PHOTO") {
      return fail(422, "M007", "용도 불일치");
    }

    if (upload.result?.status !== "READY") {
      return fail(422, "M006", "업로드 미완료");
    }
  }

  return null;
}

function queue(tip: MockTip, operation: "CREATE" | "UPDATE") {
  const jobId = `550e8400-e29b-41d4-b716-${String(mockDb.nextTipId++).padStart(12, "0")}`;
  const uploads = tip.pending?.uploadIds ?? tip.uploadIds;
  tip.state = { ...tip.state, status: tip.state.status === "PUBLISHED" ? "PUBLISHED" : "REGISTERING", jobId, operation, operationStatus: "QUEUED", error: null, retryable: false,
    steps: { media: uploads.length ? "QUEUED" : "SKIPPED", spatial: tip.pending?.placement ? "QUEUED" : "SUCCEEDED", content: "QUEUED" } };
  tip.checks = 0;
  return { tipId: tip.state.tipId, jobId, status: tip.state.status, operation, operationStatus: "QUEUED", version: tip.state.version };
}

function advance(tip: MockTip) {
  if (!["QUEUED", "RUNNING"].includes(tip.state.operationStatus ?? "")) {
    return;
  }

  tip.checks += 1;

  if (tip.checks < 2) {
    tip.state.operationStatus = "RUNNING";
    return;
  }

  const pending = tip.pending;

  if (!pending || tip.state.status === "DELETED") {
    return;
  }

  tip.updatedAt = formatServerDateTime(new Date());

  if (tip.state.operation === "CREATE") {
    tip.detail.publishedAt = tip.updatedAt;
  }

  tip.detail.body = pending.body ?? tip.detail.body;
  tip.detail.category = pending.category ?? tip.detail.category;

  if (pending.location) {
    tip.detail.location = { latitude: pending.location.latitude, longitude: pending.location.longitude };
  }

  tip.detail.spatialReference = pending.spatialReference ?? tip.detail.spatialReference;
  tip.detail.placement = pending.placement ?? tip.detail.placement;
  tip.uploadIds = pending.uploadIds ?? tip.uploadIds;

  tip.detail.photos = tip.uploadIds.map((id) => {
    return { imageId: tip.detail.id + 800, url: `https://example.com/mock-tip/${id}.jpg`, attribution: null };
  });

  tip.state = { ...tip.state, status: "PUBLISHED", operationStatus: "SUCCEEDED", version: tip.state.version + (tip.state.operation === "UPDATE" ? 1 : 0),
    steps: { media: tip.detail.photos.length ? "SUCCEEDED" : "SKIPPED", spatial: "SUCCEEDED", content: "SUCCEEDED" } };
  tip.pending = null;
}

async function handle(request: Request, action: () => Promise<Response>): Promise<Response> {
  if (!request.headers.get("Authorization")?.startsWith("Bearer ")) {
    return fail(401, "A001", "인증 필요");
  }

  try {
    return await action();
  } catch (error) {
    return error instanceof ApiError ? fail(error.status, error.code, "팁 요청 검증 실패") : fail(400, "C002", "요청 형식 오류");
  }
}

async function idempotent(request: Request, operation: string, action: () => Promise<Response>): Promise<Response> {
  const key = request.headers.get("Idempotency-Key");

  if (!key) {
    return fail(400, "C001", "멱등키 필요");
  }

  const text = await request.clone().text();
  const mapKey = `${operation}:${key}`;
  const previous = mockDb.tipIdempotency.get(mapKey);

  if (previous) {
    return previous.request === text ? ok(previous.data, previous.status) : fail(409, "C005", "멱등키 충돌");
  }

  const response = await action();

  if (response.ok) {
    const envelope: { data: unknown } = await response.clone().json();
    mockDb.tipIdempotency.set(mapKey, { request: text, data: envelope.data, status: response.status });
  }

  return response;
}

function find(value: string | readonly string[] | undefined) {
  const id = readId(value);
  return id === null ? undefined : mockDb.tips.get(id);
}

function mine(tip: MockTip | undefined): Response | null {
  if (!tip || tip.state.status === "DELETED") {
    return fail(404, "T012", "팁 없음");
  }

  return tip.detail.author.id !== 1 ? fail(403, "T002", "본인 아님") : null;
}

function paginate<T>(items: T[], cursor: string | null, limitText: string | number | null) {
  const start = cursor === null ? 0 : Number(cursor);
  const limit = limitText === null ? 20 : Number(limitText);

  if (!Number.isSafeInteger(start) || start < 0 || !Number.isSafeInteger(limit) || limit <= 0) {
    throw new ApiError(400, "C002", "페이지 형식 오류");
  }

  return { items: items.slice(start, start + limit), nextCursor: start + limit < items.length ? String(start + limit) : null };
}

export const tipHandlers = [
  http.post<never, { location: LocationEvidenceDto; radiusMeters?: number; category?: string; cursor?: string; limit?: number }>("*/api/v1/tips/search", ({ request }) => {
    return handle(request, async () => {
      const body: { location: LocationEvidenceDto; radiusMeters?: number; category?: string; cursor?: string; limit?: number } = await request.json();
      validateTipLocation(body.location);

      if (body.radiusMeters !== undefined && body.radiusMeters !== 50) {
        return fail(400, "C003", "탐색 반경은 50m");
      }

      const items = [...mockDb.tips.values()].filter((tip) => {
        return tip.state.status === "PUBLISHED" && distance(body.location, tip) <= 50;
      }).map((tip) => {
        const distanceMeters = distance(body.location, tip);
        return { id: tip.detail.id, category: tip.detail.category, ...tip.detail.location, summary: tip.detail.body, spatialReference: tip.detail.spatialReference, placement: tip.detail.placement, distanceMeters, canInteract: distanceMeters <= TIP_INTERACTION_RADIUS_METERS };
      }).sort((a, b) => {
        return a.distanceMeters - b.distanceMeters;
      });

      if (body.limit !== undefined && body.limit > 10) {
        return fail(400, "C003", "최대 10개");
      }

      return ok({ ...paginate(items, body.cursor ?? null, body.limit ?? 10), totalCount: items.length, appliedRadiusMeters: 50, interactionRadiusMeters: TIP_INTERACTION_RADIUS_METERS });
    });
  }),
  http.post<{ tipId: string }, { location: LocationEvidenceDto }>("*/api/v1/tips/:tipId/view", ({ request, params }) => {
    return handle(request, async () => {
      const body: { location: LocationEvidenceDto } = await request.json();
      validateTipLocation(body.location);
      const tip = find(params.tipId);

      if (!tip || tip.state.status !== "PUBLISHED") {
        return fail(404, "T001", "공개 팁 없음");
      }

      if (distance(body.location, tip) > TIP_INTERACTION_RADIUS_METERS) {
        return fail(422, "T004", "상호작용 거리 초과");
      }

      return ok(tip.detail);
    });
  }),
  http.post<never, CreateTipRequestDto>("*/api/v1/tips", ({ request }) => {
    return handle(request, () => {
      return idempotent(request, "create", async () => {
        const body: CreateTipRequestDto = await request.json();
        body.body = normalizeTipBody(body.body);
        body.category = validateTipType(body.category);
        validateTipSpatialFields(body, true);
        const error = uploadError(body.uploadIds);

        if (error) {
          return error;
        }

        const tipId = mockDb.nextTipId++;
        const createdAt = formatServerDateTime(new Date());
        const tip: MockTip = {
          detail: { ...tipFixture, id: tipId, createdAt, category: body.category, body: body.body, author: { id: 1, nickname: "나" }, photos: [], location: body.location, spatialReference: body.spatialReference, placement: body.placement, reactions: { likes: 0, dislikes: 0, myReaction: null } },
          updatedAt: createdAt,
          state: { tipId, status: "REGISTERING", jobId: null, steps: { media: "SKIPPED", spatial: "QUEUED", content: "QUEUED" }, error: null, retryable: false, operation: null, operationStatus: null, version: 1 },
          pending: body, uploadIds: [], checks: 0,
        };
        mockDb.tips.set(tipId, tip);
        const result = queue(tip, "CREATE");
        return ok({ tipId, jobId: result.jobId, status: "REGISTERING" }, 202);
      });
    });
  }),
  http.get("*/api/v1/tips/:tipId/status", ({ request, params }) => {
    return handle(request, async () => {
      const tip = find(params.tipId);

      if (tip?.detail.author.id === 1 && tip.state.status === "DELETED") {
        return ok(tip.state);
      }

      const error = mine(tip);

      if (error || !tip) {
        return error ?? fail(404, "T012", "팁 없음");
      }

      advance(tip);
      return ok(tip.state);
    });
  }),
  http.put<{ tipId: string }, { reaction: string; location: LocationEvidenceDto }>("*/api/v1/tips/:tipId/reaction", ({ request, params }) => {
    return handle(request, async () => {
      const body: { reaction: string; location: LocationEvidenceDto } = await request.json();
      validateTipLocation(body.location);
      const tip = find(params.tipId);

      if (!tip || tip.state.status !== "PUBLISHED") {
        return fail(404, "T001", "공개 팁 없음");
      }

      if (distance(body.location, tip) > TIP_INTERACTION_RADIUS_METERS) {
        return fail(422, "T004", "상호작용 거리 초과");
      }

      if (tip.detail.author.id === 1) {
        return fail(409, "T009", "본인 팁 반응 불가");
      }

      if (!["LIKE", "DISLIKE", "NONE"].includes(body.reaction)) {
        return fail(400, "C003", "반응 형식 오류");
      }

      const reactions = tip.detail.reactions;
      reactions.likes += Number(body.reaction === "LIKE") - Number(reactions.myReaction === "LIKE");
      reactions.dislikes += Number(body.reaction === "DISLIKE") - Number(reactions.myReaction === "DISLIKE");
      reactions.myReaction = body.reaction === "NONE" ? null : body.reaction;
      return ok({ tipId: tip.detail.id, ...reactions });
    });
  }),
  http.get("*/api/v1/users/me/tips", ({ request }) => {
    return handle(request, async () => {
      const params = new URL(request.url).searchParams;

      const items = [...mockDb.tips.values()].filter((tip) => {
        return tip.detail.author.id === 1 && tip.state.status !== "DELETED" && (!params.has("status") || params.get("status") === tip.state.status);
      }).map((tip) => {
        return { id: tip.detail.id, category: tip.detail.category, status: tip.state.status, createdAt: tip.detail.createdAt, updatedAt: tip.updatedAt };
      });

      return ok(paginate(items, params.get("cursor"), params.get("limit")));
    });
  }),
  http.get("*/api/v1/users/me/tips/:tipId", ({ request, params }) => {
    return handle(request, async () => {
      const tip = find(params.tipId);
      const error = mine(tip);

      if (error || !tip) {
        return error ?? fail(404, "T012", "팁 없음");
      }

      return ok({ tipId: tip.detail.id, body: tip.detail.body,

        photos: tip.detail.photos.map((photo) => {
          return { imageId: photo.imageId, url: photo.url, expiresAt: "2100-01-01 00:00:00+09" };
        }),

        category: tip.detail.category, status: tip.state.status, version: tip.state.version, publishedAt: tip.state.status === "PUBLISHED" ? tip.detail.publishedAt : null, createdAt: tip.detail.createdAt, updatedAt: tip.updatedAt });
    });
  }),
  http.patch<{ tipId: string }, UpdateTipRequestDto>("*/api/v1/tips/:tipId", ({ request, params }) => {
    return handle(request, () => {
      return idempotent(request, `update:${params.tipId}`, async () => {
        const tip = find(params.tipId);
        const error = mine(tip);

        if (error || !tip) {
          return error ?? fail(404, "T012", "팁 없음");
        }

        const body: UpdateTipRequestDto = await request.json();

        if (body.version !== tip.state.version) {
          return fail(409, "C004", "version 충돌");
        }

        if (["QUEUED", "RUNNING"].includes(tip.state.operationStatus ?? "")) {
          return fail(409, "T010", "수정 중");
        }

        if (body.body !== undefined) {
          body.body = normalizeTipBody(body.body);
        }

        if (body.category !== undefined) {
          body.category = validateTipType(body.category);
        }

        validateTipSpatialFields(body, false);
        const fileError = uploadError(body.uploadIds);

        if (fileError) {
          return fileError;
        }

        if (body.location && distance(body.location, tip) > 5) {
          return fail(422, "T004", "재배치 거리 초과");
        }

        if (body.body === undefined && body.category === undefined && body.uploadIds === undefined && body.placement === undefined) {
          return fail(400, "C003", "변경 없음");
        }

        tip.pending = body;
        return ok(queue(tip, "UPDATE"), 202);
      });
    });
  }),
  http.post<{ tipId: string }, RetryTipRequestDto>("*/api/v1/tips/:tipId/retry", ({ request, params }) => {
    return handle(request, () => {
      return idempotent(request, `retry:${params.tipId}`, async () => {
        const tip = find(params.tipId);
        const error = mine(tip);

        if (error || !tip) {
          return error ?? fail(404, "T012", "팁 없음");
        }

        if (!tip.pending) {
          return tip.state.status === "PUBLISHED" && tip.state.operationStatus !== "FAILED" ? ok({ tipId: tip.detail.id, status: "PUBLISHED" }) : fail(409, "T008", "수정안 없음");
        }

        if (tip.state.operationStatus !== "FAILED" || !tip.state.retryable) {
          return fail(409, "T008", "재시도할 실패 작업 없음");
        }

        const body: RetryTipRequestDto = await request.json();
        normalizeTipBody(tip.pending.body ?? tip.detail.body);
        const fileError = uploadError(body.replacementUploadIds);

        if (fileError) {
          return fileError;
        }

        if ("version" in tip.pending && tip.pending.version !== tip.state.version) {
          return fail(409, "C004", "version 충돌");
        }

        if (body.replacementUploadIds !== undefined) {
          tip.pending.uploadIds = body.replacementUploadIds;
        }

        if (body.location || body.spatialReference || body.placement) {
          Object.assign(tip.pending, validateTipSpatialFields(body, true));
        }

        return ok(queue(tip, tip.state.operation === "UPDATE" ? "UPDATE" : "CREATE"), 202);
      });
    });
  }),
  http.delete("*/api/v1/tips/:tipId", ({ request, params }) => {
    return handle(request, async () => {
      const tip = find(params.tipId);

      if (tip?.detail.author.id === 1 && tip.state.status === "DELETED") {
        return ok({ tipId: tip.detail.id, status: "DELETED" });
      }

      const error = mine(tip);

      if (error || !tip) {
        return error ?? fail(404, "T012", "팁 없음");
      }

      if (Number(new URL(request.url).searchParams.get("version")) !== tip.state.version) {
        return fail(409, "C004", "version 충돌");
      }

      tip.state.status = "DELETED";
      tip.state.version += 1;
      tip.pending = null;
      return ok({ tipId: tip.detail.id, status: "DELETED" });
    });
  }),
];
