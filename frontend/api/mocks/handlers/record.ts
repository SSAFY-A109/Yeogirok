import { http, HttpResponse } from "msw";

import type { ActualVisit, Material, PlaceInput, PublicationInput, RecordListItemDto, RecordPhoto, RecordPlace, VisitInput } from "../../../features/record/model";
import { formatServerDateTime, parseServerDateTime } from "../../../lib/date";
import { mockDb } from "../db";
import { makeMockRecord, mockVisitPlace, publicRecordAuthors, publicRecordDays, type MockRecord } from "../fixtures/record";
import { fail, ok } from "./respond";

const root = "*/api/v1/records/:recordId";
const mockFiles = "https://uploads.yeogirok.test/record-file";
const dayMs = 24 * 60 * 60 * 1000;

const authenticated = (request: Request) => {
  return request.headers.get("Authorization")?.startsWith("Bearer ");
};

const unauthorized = () => {
  return fail(401, "A001", "인증 필요");
};

const conflict = () => {
  return fail(409, "C004", "버전 불일치");
};

const fileMissing = () => {
  return fail(404, "M004", "파일 없음");
};

const toDateTime = (time: number) => {
  return formatServerDateTime(new Date(time));
};

const fakeUuid = () => {
  return `550e8400-e29b-41d4-a716-${String(mockDb.nextRecordEntityId++).padStart(12, "0")}`;
};

function recordAccess(recordId: unknown, request: Request) {
  if (!authenticated(request)) {
    return unauthorized();
  }

  const record = mockDb.records.get(Number(recordId));
  const room = record?.detail.roomId != null ? mockDb.rooms.get(record.detail.roomId) : null;

  if (!record || (record.detail.roomId !== null && !room)) {
    return fail(404, "RE001", "기록 없음");
  }

  if (room && !room.detail.members.some((member) => {
    return member.me;
  })) {
    return fail(403, "RE002", "참여자 아님");
  }

  return record;
}

async function idempotent<T extends Response>(request: Request, write: () => T) {
  const key = request.headers.get("Idempotency-Key");

  if (!key) {
    return fail(400, "C001", "Idempotency-Key 필요");
  }

  const hash = `${request.method} ${request.url} ${await request.text()}`;
  const previous = mockDb.recordIdempotency.get(key);

  if (previous) {
    return previous.request === hash ? ok(previous.data, previous.status) : fail(409, "C005", "멱등키 충돌");
  }

  const response = write();

  if (response.ok) {
    const envelope = await response.clone().json() as { data: unknown };
    mockDb.recordIdempotency.set(key, { request: hash, data: envelope.data, status: response.status });
  }

  return response;
}

function collectPhotos(record: MockRecord) {
  const detail = record.detail;

  const sessions = [...mockDb.uploads.values()].filter((upload) => {
    return upload.request.recordId === detail.id;
  });

  detail.uploads = sessions.map(({ request, session, result }) => {
    return {
      uploadId: session.uploadId, imageId: result?.imageId ?? null, materialId: result?.materialId ?? null,
      purpose: request.purpose === "RECEIPT" ? "RECEIPT" : "RECORD_PHOTO",
      status: result ? "READY" : "UPLOADING", uploadedBy: 1, error: null, retryable: !result,
    };
  });

  detail.photoPool = sessions.flatMap(({ session, result }): RecordPhoto[] => {
    return result?.imageId != null ? [{
      imageId: result.imageId, url: `${mockFiles}/${session.uploadId}`, attribution: null, uploadedBy: 1,

      visitId: detail.actualVisits.find((visit) => {
        return visit.photos.some((photo) => {
          return photo.imageId === result.imageId;
        });
      })?.id ?? null,
    }] : [];
  });
}

function syncProgress(record: MockRecord) {
  const { detail, draft } = record;
  collectPhotos(record);
  const materials = [...record.materials.values()];

  for (const item of materials) {
    if (item.status === "UPLOADED" && Date.now() - parseServerDateTime(item.uploadAcceptedAt, "uploadAcceptedAt").getTime() >= 1000) {
      item.status = "READY";
      item.version += 1;
      item.reviewStatus = "REQUIRED";
      item.needsReview = true;
    }

    if (item.originalDeleteAfter && parseServerDateTime(item.originalDeleteAfter, "originalDeleteAfter").getTime() <= Date.now()) {
      item.originalAvailable = false;
    }
  }

  detail.materials = materials.map(({ materialId, version, type, status, reviewStatus, needsReview, missingFields, canUseForDraft, error }) => {
    return { materialId, version, type, status, reviewStatus, needsReview, missingFields, canUseForDraft, error };
  });

  if (detail.progress.stage === "EDIT" || detail.progress.stage === "COMPLETED") {
    return;
  }

  if (draft) {
    draft.status = Date.now() >= draft.readyAt ? "SUCCEEDED" : "QUEUED";
    detail.progress = { stage: draft.status === "SUCCEEDED" ? "REVIEW" : "PROCESSING", draftKey: draft.draftKey, jobId: draft.jobId };
  } else {
    detail.progress.stage = materials.some((material) => {
      return material.needsReview;
    }) ? "RECEIPT_REVIEW"
      : detail.photoPool.length && materials.some((material) => {
        return material.canUseForDraft;
      }) ? "READY_TO_GENERATE" : "UPLOAD";
  }
}

function changed(record: MockRecord) {
  record.detail.version += 1;
  record.detail.status = "DRAFT";
  record.detail.progress = { stage: "EDIT", draftKey: null, jobId: null };
}

function resolvePlace(input: Partial<PlaceInput>, candidates: RecordPlace[] = []): RecordPlace | undefined {
  if (input.customPlace) {
    return { ...mockVisitPlace, ...input.customPlace, address: input.customPlace.address ?? null };
  }

  if (input.placeRef) {
    return candidates.find((place) => {
      return place.providerPlaceId === input.placeRef?.providerPlaceId;
    });
  }

  return undefined;
}

function validVisitDate(visitDate: string, visitedAt: string) {
  try {
    parseServerDateTime(visitedAt, "visitedAt");
    return /^\d{4}-\d{2}-\d{2}$/.test(visitDate) && visitedAt.startsWith(visitDate);
  } catch {
    return false;
  }
}

function publicationPreview(record: MockRecord, body: PublicationInput) {
  const room = mockDb.rooms.get(record.detail.roomId ?? 0);
  const authors = room ? publicRecordAuthors(room) : [];
  const description = body.description ?? record.publication?.description ?? "";

  const days = publicRecordDays(record, () => {
    return body.includePhotos;
  });

  const selectionHash = JSON.stringify({
    version: record.detail.version, title: body.title, description, includePhotos: body.includePhotos,

    authors: authors.map((author) => {
      return author.id;
    }),

    days: days.map((day) => {
      return day.items.map((visit) => {
        return {
          id: visit.visitId, order: visit.order,

          photoIds: visit.photos.map((photo) => {
            return photo.imageId;
          }),
        };
      });
    }),
  });
  return { preview: { title: body.title, authors, description, days }, selectionHash, excluded: [], warnings: [] };
}

function validPublicationInput(body: PublicationInput) {
  return Number.isSafeInteger(body.version) && typeof body.title === "string" && body.title.trim() !== "" &&
    typeof body.includePhotos === "boolean" && (body.description === undefined || typeof body.description === "string");
}

export const recordHandlers = [
  http.post(`${root}/publication-preview`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const body = await request.json() as PublicationInput;

    if (!validPublicationInput(body)) {
      return fail(400, "C002", "미리보기 입력 오류");
    }

    if (body.version !== record.detail.version) {
      return conflict();
    }

    return ok(publicationPreview(record, body));
  }),
  http.post(`${root}/publications`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const body = await request.clone().json() as PublicationInput & { selectionHash: string };

    return idempotent(request, () => {
      if (!validPublicationInput(body) || typeof body.selectionHash !== "string") {
        return fail(400, "C002", "공개 입력 오류");
      }

      if (record.publication) {
        return fail(409, "CO011", "이미 게시 중");
      }

      if (record.detail.status !== "COMPLETED" || record.detail.actualVisits.length === 0) {
        return fail(422, "RE019", "기록 완성 필요");
      }

      const expected = publicationPreview(record, body);

      if (record.detail.version !== body.version || expected.selectionHash !== body.selectionHash) {
        return fail(409, "CO010", "미리보기 변경");
      }

      const publishedAt = toDateTime(Date.now());
      record.publication = {
        description: expected.preview.description, publishedAt,

        imageIds: expected.preview.days.flatMap((day) => {
          return day.items.flatMap((visit) => {
            return visit.photos.map((photo) => {
              return photo.imageId;
            });
          });
        }),
      };
      record.detail.title = body.title;
      record.detail.publishedCourseId = record.detail.id;
      record.detail.version += 1;
      return ok({ courseId: record.detail.id, publishedAt, version: record.detail.version }, 201);
    });
  }),
  http.get("*/api/v1/records", ({ request }) => {
    if (!authenticated(request)) {
      return unauthorized();
    }

    const { searchParams } = new URL(request.url);
    const offset = Number(searchParams.get("cursor") ?? 0);
    const limit = Number(searchParams.get("limit") ?? 20);
    const roomId = searchParams.get("roomId");
    const status = searchParams.get("status");

    if (!Number.isSafeInteger(offset) || offset < 0 || !Number.isSafeInteger(limit) || limit < 1) {
      return fail(400, "C002", "목록 입력 오류");
    }

    const items = [...mockDb.records.values()].flatMap((record): RecordListItemDto[] => {
      const detail = record.detail;
      const room = detail.roomId === null ? null : mockDb.rooms.get(detail.roomId);

      if (!room || !room.detail.members.some((member) => {
        return member.me;
      }) || (roomId && Number(roomId) !== detail.roomId) ||
        (status && status !== detail.status)) {
        return [];
      }

      syncProgress(record);
      const { id, title, roomId: sourceRoom, status: recordStatus, startDate, endDate, progress, version, publishedCourseId } = detail;
      return [{
        id, title, roomId: sourceRoom, status: recordStatus, startDate, endDate, progress, regionName: id === 1 ? "부산" : null,
        members: room.detail.members, memberCount: room.detail.members.length, placeCount: detail.actualVisits.length,
        publication: publishedCourseId === null || !record.publication ? null
          : { courseId: publishedCourseId, publishedAt: record.publication.publishedAt, version },
      }];
    });

    return ok({ items: items.slice(offset, offset + limit), nextCursor: offset + limit < items.length ? String(offset + limit) : null });
  }),
  http.post("*/api/v1/rooms/:roomId/records", async ({ request, params }) => {
    if (!authenticated(request)) {
      return unauthorized();
    }

    const room = mockDb.rooms.get(Number(params.roomId));

    if (!room) {
      return fail(404, "RO001", "여행방 없음");
    }

    if (!room.detail.members.some((member) => {
      return member.me;
    })) {
      return fail(403, "RO002", "참여자 아님");
    }

    const { title } = await request.clone().json() as { title?: string };

    return idempotent(request, () => {
      if (room.detail.recordId != null) {
        return fail(409, "RE017", "기록 있음");
      }

      const today = formatServerDateTime(new Date()).slice(0, 10);

      if (!room.detail.endDate || room.detail.endDate >= today) {
        return fail(422, "RE024", "여행 미종료");
      }

      const id = mockDb.nextRecordEntityId++;
      const record = makeMockRecord(id, room, title);
      mockDb.records.set(id, record);
      room.detail.recordId = id;
      return ok({ recordId: id, roomId: room.detail.roomId, status: "DRAFT", version: 1 }, 201);
    });
  }),
  http.get(root, ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    syncProgress(record);
    const members = mockDb.rooms.get(record.detail.roomId ?? 0)?.detail.members;
    return ok({ ...record.detail,

      members: members?.map(({ userId, nickname, me }) => {
        return { userId, nickname, isCreator: me };
      }) ?? record.detail.members });
  }),
  http.get(`${root}/materials`, ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    syncProgress(record);
    return ok({ items: [...record.materials.values()] });
  }),
  http.post(`${root}/materials`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const { type, uploadId } = await request.clone().json() as { type: Material["type"]; uploadId: string };

    return idempotent(request, () => {
      const upload = mockDb.uploads.get(uploadId);

      if (!upload) {
        return fileMissing();
      }

      if (upload.request.recordId !== record.detail.id) {
        return fail(403, "M005", "소속 불일치");
      }

      if (upload.request.purpose !== "RECEIPT") {
        return fail(422, "M007", "용도 불일치");
      }

      const materialId = upload.result?.materialId;

      if (!materialId) {
        return fail(422, "M006", "미완료");
      }

      if (record.materials.has(materialId)) {
        return fail(409, "RE009", "이미 연결됨");
      }

      const jobId = fakeUuid();
      const acceptedAt = upload.acceptedAt ?? Date.now();
      record.materials.set(materialId, {
        materialId, type, jobId, version: 1, status: "UPLOADED", reviewStatus: "PENDING",
        needsReview: false, canUseForDraft: false, missingFields: ["merchantName", "paidDate", "paidTime", "place"], error: null,
        extracted: { placeCandidates: [] }, values: { merchantName: null, paidAt: null, paidDate: null, paidTime: null, place: null },
        originalAvailable: true, originalDeleteAfter: toDateTime(acceptedAt + dayMs), uploadAcceptedAt: toDateTime(acceptedAt),
      });
      return ok({ materialId, recordId: record.detail.id, status: "UPLOADED", version: 1, jobId, ocrStatus: "QUEUED" }, 201);
    });
  }),
  http.patch(`${root}/materials/:materialId/review`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const body = await request.clone().json() as PlaceInput & { version: number; merchantName: string; paidAt: string };

    return idempotent(request, () => {
      syncProgress(record);
      const material = record.materials.get(String(params.materialId));

      if (!material) {
        return fail(404, "RE008", "자료 없음");
      }

      if (material.version !== body.version) {
        return conflict();
      }

      if (material.status !== "READY") {
        return fail(409, "RE022", "처리 중");
      }

      const place = resolvePlace(body, material.extracted?.placeCandidates);

      if (!place || !body.merchantName?.trim() || !validVisitDate(body.paidAt?.slice(0, 10), body.paidAt)) {
        return fail(400, "C001", "보정값 누락");
      }

      Object.assign(material, {
        version: material.version + 1, status: "READY", needsReview: false, reviewStatus: "CONFIRMED", missingFields: [],
        values: { place, merchantName: body.merchantName, paidAt: body.paidAt, paidDate: body.paidAt.slice(0, 10), paidTime: body.paidAt.slice(11, 19) },
        canUseForDraft: true, originalAvailable: false,
      });
      const original = mockDb.uploads.get(material.materialId);

      if (original) {
        original.bytes = undefined;
      }

      return ok(material);
    });
  }),
  http.post(`${root}/materials/:materialId/ocr`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const { version } = await request.clone().json() as { version: number };

    return idempotent(request, () => {
      syncProgress(record);
      const material = record.materials.get(String(params.materialId));

      if (!material) {
        return fail(404, "RE008", "자료 없음");
      }

      if (!material.originalAvailable) {
        return fileMissing();
      }

      if (material.version !== version) {
        return conflict();
      }

      if (material.status === "UPLOADED" || material.status === "EXTRACTING") {
        return fail(409, "RE022", "처리 중");
      }

      Object.assign(material, { version: version + 1, jobId: fakeUuid(), status: "UPLOADED" });
      return ok({ materialId: material.materialId, version: material.version, jobId: material.jobId, status: "QUEUED" }, 202);
    });
  }),
  http.get(`${root}/:kind/:fileId/access`, ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    syncProgress(record);

    if (params.kind !== "images" && params.kind !== "materials") {
      return fileMissing();
    }

    const receipt = params.kind === "materials" ? record.materials.get(String(params.fileId)) : undefined;

    if (params.kind === "materials" && !receipt?.originalAvailable) {
      return fileMissing();
    }

    const upload = [...mockDb.uploads.values()].find((item) => {
      return item.request.recordId === record.detail.id &&
      (receipt ? item.result?.materialId === receipt.materialId : item.result?.imageId === Number(params.fileId));
    });

    if (!upload?.bytes) {
      return fileMissing();
    }

    return ok({
      url: `${mockFiles}/${upload.session.uploadId}`, contentType: upload.request.contentType,
      expiresAt: receipt?.originalDeleteAfter ?? toDateTime(Date.now() + 60 * 60 * 1000),
    });
  }),
  http.get(`${mockFiles}/:uploadId`, ({ params }) => {
    const upload = mockDb.uploads.get(String(params.uploadId));

    if (!upload?.bytes || (upload.request.purpose === "RECEIPT" && (upload.acceptedAt ?? 0) + dayMs <= Date.now())) {
      return fileMissing();
    }

    return new HttpResponse(upload.bytes, { headers: { "Content-Type": upload.request.contentType } });
  }),
  http.post(`${root}/drafts`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const { baseVersion, imageIds, materials } = await request.clone().json() as {
      baseVersion: number; imageIds: number[]; materials: { materialId: string; version: number }[];
    };

    return idempotent(request, () => {
      syncProgress(record);

      if (record.detail.version !== baseVersion) {
        return conflict();
      }

      if (!imageIds?.length || !materials?.length) {
        return fail(400, "C003", "사진과 영수증 필요");
      }

      if (imageIds.some((id) => {
        return !record.detail.photoPool.some((photo) => {
          return photo.imageId === id;
        });
      })) {
        return fileMissing();
      }

      const receipts = materials.map(({ materialId }) => {
        return record.materials.get(materialId);
      });

      if (receipts.some((receipt, index) => {
        return receipt && receipt.version !== materials[index].version;
      })) {
        return conflict();
      }

      if (receipts.some((receipt) => {
        return !receipt?.canUseForDraft;
      })) {
        return fail(409, "RE022", "영수증 확인 필요");
      }

      const draftKey = fakeUuid();
      const jobId = fakeUuid();
      record.draft = {
        draftKey, jobId, applied: false, readyAt: Date.now() + 1800, status: "QUEUED", baseVersion, draftVersion: 1,
        warnings: [], error: null, sourcePriority: ["MANUAL", "RECEIPT", "PHOTO", "PLAN"],

        items: receipts.flatMap((receipt, index) => {
          return receipt ? [{
            itemId: index + 1, sourceMaterialIds: [receipt.materialId], sourcePhotoIds: [], plannedItemId: null,
            placeCandidates: [], proposedPlace: receipt.values.place, visitDate: receipt.values.paidDate,
            proposedVisitedAt: receipt.values.paidAt, needsReview: false,
            evidence: { sourceTypes: ["RECEIPT"], fieldSources: { place: "RECEIPT", visitDate: "RECEIPT", proposedVisitedAt: "RECEIPT" }, warnings: [] },
          }] : [];
        }),
      };
      syncProgress(record);
      return ok({ draftKey, jobId, status: "QUEUED" }, 202);
    });
  }),
  http.get("*/api/v1/jobs/:jobId", ({ params, request }) => {
    if (!authenticated(request)) {
      return unauthorized();
    }

    const found = [...mockDb.records.values()].find((record) => {
      return record.draft?.jobId === params.jobId ||
      [...record.materials.values()].some((item) => {
        return item.jobId === params.jobId;
      });
    });

    if (!found) {
      return fail(404, "J001", "작업 없음");
    }

    const record = recordAccess(found.detail.id, request);

    if (record instanceof Response) {
      return fail(403, "J003", "연결 자원 접근 불가");
    }

    syncProgress(record);

    const draft = record.draft;

    if (draft && draft.jobId === params.jobId) {
      return ok({
        jobId: draft.jobId, type: "RECORD_GENERATION", status: draft.status,
        result: draft.status === "SUCCEEDED" ? { type: "RECORD_DRAFT", draftKey: draft.draftKey } : null,
        error: null, retryable: false,
      });
    }

    const material = [...record.materials.values()].find((item) => {
      return item.jobId === params.jobId;
    });

    if (!material) {
      return fail(404, "J001", "작업 없음");
    }

    return ok({
      jobId: material.jobId, type: "RECEIPT_OCR", status: material.status === "READY" ? "SUCCEEDED" : "QUEUED",
      result: material.status === "READY" ? { type: "RECEIPT_OCR", materialId: material.materialId, materialVersion: material.version } : null,
      error: null, retryable: false,
    });
  }),
  http.get(`${root}/drafts/:draftKey`, ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    syncProgress(record);
    const draft = record.draft;

    if (!draft || draft.draftKey !== params.draftKey) {
      return fail(404, "RE011", "초안 없음");
    }

    const { draftKey, status, baseVersion, draftVersion, items, warnings, error, sourcePriority } = draft;
    return ok({ draftKey, status, baseVersion, draftVersion, items, warnings, error, sourcePriority });
  }),
  http.post(`${root}/visits`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const { version, draftKey, draftVersion, items } = await request.clone().json() as {
      version: number; draftKey?: string; draftVersion?: number; items: VisitInput[];
    };

    return idempotent(request, () => {
      const draft = record.draft;

      if (version !== record.detail.version) {
        return conflict();
      }

      if (draftKey && (!draft || draft.draftKey !== draftKey)) {
        return fail(404, "RE011", "초안 없음");
      }

      if (draftKey && draft?.applied) {
        return fail(409, "RE012", "이미 적용된 초안");
      }

      if (draftKey && (draft?.draftVersion !== draftVersion || draft?.baseVersion !== version)) {
        return conflict();
      }

      const added: ActualVisit[] = [];

      for (const item of items ?? []) {
        const source = draft?.items.find((row) => {
          return row.itemId === item.itemId;
        });

        const candidates = [...(source?.placeCandidates ?? []), ...(source?.proposedPlace ? [source.proposedPlace] : [])];
        const place = resolvePlace(item, candidates);

        if (!place) {
          return fail(400, "RE018", "장소 입력 누락");
        }

        if (!validVisitDate(item.visitDate, item.visitedAt)) {
          return fail(400, "C002", "날짜·시각 오류");
        }

        added.push({ id: mockDb.nextRecordEntityId++, place, plannedItemId: item.plannedItemId ?? null,
          visitDate: item.visitDate, visitedAt: item.visitedAt, order: item.order, photos: [], stories: [] });
      }

      if (draftKey && draft) {
        draft.applied = true;
      }

      if (added.length) {
        record.detail.actualVisits.push(...added); changed(record);
      }

      return ok({ visitIds: added.map((item) => {
        return item.id;
      }),

      version: record.detail.version, status: record.detail.status }, 201);
    });
  }),
  http.patch(`${root}/visits`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const { version, items, removeVisitIds = [], confirmDeleteStories } = await request.json() as {
      version: number; items: (Partial<VisitInput> & { visitId: number; visitDate: string; visitedAt: string })[];
      removeVisitIds?: number[]; confirmDeleteStories?: boolean;
    };
    const existing = record.detail.actualVisits;

    if (version !== record.detail.version) {
      return conflict();
    }

    if ([...removeVisitIds, ...items.map((item) => {
      return item.visitId;
    })].some((id) => {
      return !existing.some((visit) => {
        return visit.id === id;
      });
    })) {
      return fail(404, "RE013", "방문 없음");
    }

    const removed = existing.filter((item) => {
      return removeVisitIds.includes(item.id);
    });

    if (removed.some((item) => {
      return item.photos.length;
    })) {
      return fail(409, "RE020", "연결된 사진 있음");
    }

    if (!confirmDeleteStories && removed.some((item) => {
      return item.stories.length;
    })) {
      return fail(409, "RE023", "이야기 삭제 확인 필요");
    }

    if (items.some((item) => {
      return !validVisitDate(item.visitDate, item.visitedAt);
    })) {
      return fail(400, "C002", "날짜·시각 오류");
    }

    const visits = existing.filter((visit) => {
      return !removeVisitIds.includes(visit.id);
    }).map((visit) => {
      const item = items.find((entry) => {
        return entry.visitId === visit.id;
      });

      if (!item) {
        return visit;
      }

      return { ...visit, place: resolvePlace(item, [visit.place]) ?? visit.place, visitDate: item.visitDate, visitedAt: item.visitedAt,
        plannedItemId: item.plannedItemId === undefined ? visit.plannedItemId : item.plannedItemId, order: item.order ?? visit.order };
    });

    if (JSON.stringify(existing) !== JSON.stringify(visits)) {
      record.detail.actualVisits = visits; changed(record);
    }

    return ok({ actualVisits: visits, status: record.detail.status, version: record.detail.version });
  }),
  http.post(`${root}/visits/:visitId/photos`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const { imageIds } = await request.clone().json() as { imageIds: number[] };

    return idempotent(request, () => {
      collectPhotos(record);

      const visit = record.detail.actualVisits.find((item) => {
        return item.id === Number(params.visitId);
      });

      if (!visit) {
        return fail(404, "RE013", "방문 없음");
      }

      if (imageIds.some((id) => {
        return !record.detail.photoPool.some((photo) => {
          return photo.imageId === id;
        });
      })) {
        return fileMissing();
      }

      const moving = record.detail.photoPool.filter((photo) => {
        return imageIds.includes(photo.imageId) && photo.visitId !== visit.id;
      });

      if (moving.length) {
        if (record.publication) {
          record.publication.imageIds = record.publication.imageIds.filter((id) => {
            return !moving.some((photo) => {
              return photo.imageId === id;
            });
          });
        }

        for (const item of record.detail.actualVisits) {
          item.photos = item.photos.filter((photo) => {
            return !moving.some((image) => {
              return image.imageId === photo.imageId;
            });
          });
        }

        visit.photos.push(...moving.map((photo) => {
          return { ...photo, visitId: visit.id };
        }));

        changed(record);
      }

      return ok({ photos: visit.photos.map(({ imageId, visitId, uploadedBy }) => {
        return { imageId, visitId, uploadedBy };
      }),

      version: record.detail.version, status: record.detail.status }, 201);
    });
  }),
  http.delete(`${root}/images/:imageId`, ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const imageId = Number(params.imageId);

    return idempotent(request, () => {
      if (Number(new URL(request.url).searchParams.get("version")) !== record.detail.version) {
        return conflict();
      }

      const entry = [...mockDb.uploads.entries()].find(([, upload]) => {
        return upload.request.recordId === record.detail.id && upload.result?.imageId === imageId;
      });

      if (!entry) {
        return fileMissing();
      }

      mockDb.uploads.delete(entry[0]);

      if (record.publication) {
        record.publication.imageIds = record.publication.imageIds.filter((id) => {
          return id !== imageId;
        });
      }

      for (const visit of record.detail.actualVisits) {
        visit.photos = visit.photos.filter((photo) => {
          return photo.imageId !== imageId;
        });
      }

      if (record.detail.progress.stage === "EDIT" || record.detail.progress.stage === "COMPLETED") {
        changed(record);
      } else {
        record.detail.version += 1;
        record.draft = null;
        record.detail.progress = { stage: "UPLOAD", jobId: null, draftKey: null };
      }

      return ok({ recordId: record.detail.id, imageId, deleted: true, status: record.detail.status, version: record.detail.version });
    });
  }),
  http.post(`${root}/visits/:visitId/stories`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const { body } = await request.clone().json() as { body: string };

    return idempotent(request, () => {
      const visit = record.detail.actualVisits.find((item) => {
        return item.id === Number(params.visitId);
      });

      if (!visit) {
        return fail(404, "RE013", "방문 없음");
      }

      if (!body?.trim()) {
        return fail(400, "C002", "본문 필요");
      }

      const storyId = mockDb.nextRecordEntityId++;
      visit.stories.push({ id: storyId, authorId: 1, body });
      changed(record);
      return ok({ storyId, authorId: 1, body, status: record.detail.status }, 201);
    });
  }),
  http.patch(`${root}/visits/:visitId/stories/:storyId`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const story = record.detail.actualVisits.find((item) => {
      return item.id === Number(params.visitId);
    })?.stories.find((item) => {
      return item.id === Number(params.storyId);
    });

    if (!story) {
      return fail(404, "RE014", "이야기 없음");
    }

    if (story.authorId !== 1) {
      return fail(403, "RE015", "작성자 아님");
    }

    const { body } = await request.json() as { body: string };

    if (!body?.trim()) {
      return fail(400, "C002", "본문 필요");
    }

    if (story.body !== body) {
      story.body = body; changed(record);
    }

    return ok({ storyId: story.id, body, status: record.detail.status, updatedAt: toDateTime(Date.now()) });
  }),
  http.post(`${root}/complete`, async ({ params, request }) => {
    const record = recordAccess(params.recordId, request);

    if (record instanceof Response) {
      return record;
    }

    const { version } = await request.clone().json() as { version: number };

    return idempotent(request, () => {
      const detail = record.detail;

      if (version !== detail.version) {
        return conflict();
      }

      if (!detail.actualVisits.length || detail.actualVisits.some((visit) => {
        return !validVisitDate(visit.visitDate, visit.visitedAt);
      })) {
        return fail(422, "RE016", "유효 방문 필요");
      }

      if (detail.status !== "COMPLETED") {
        detail.status = "COMPLETED";
        detail.progress = { stage: "COMPLETED", jobId: null, draftKey: null };
        detail.version += 1;
      }

      return ok({ recordId: detail.id, status: detail.status, version: detail.version });
    });
  }),
];
