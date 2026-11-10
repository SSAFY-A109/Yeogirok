import { http } from "msw";

import { ApiError } from "../../../api/errors";
import { assertUploadId, toUploadSession, validateUploadRequest, type CreateUploadRequestDto } from "../../../features/media/model";
import { mockDb } from "../db";
import type { MockUpload } from "../fixtures/media";
import { fail, ok } from "./respond";

export const mediaHandlers = [
  http.post<never, CreateUploadRequestDto>("*/api/v1/uploads", async ({ request }) => {
    if (!request.headers.get("Authorization")?.startsWith("Bearer ")) {
      return fail(401, "A001", "인증 필요");
    }

    const key = request.headers.get("Idempotency-Key");

    if (!key) {
      return fail(400, "C001", "Idempotency-Key 필요");
    }

    const body: CreateUploadRequestDto = await request.json();

    try {
      validateUploadRequest(body);
    } catch (error) {
      return error instanceof ApiError ? fail(error.status, error.code, "업로드 요청 검증 실패") : fail(400, "C002", "형식 오류");
    }

    const text = JSON.stringify(body);
    const previous = mockDb.uploadIdempotency.get(key);

    if (previous) {
      if (previous.request !== text) {
        return fail(409, "C005", "멱등키 충돌");
      }

      const upload = [...mockDb.uploads.values()].find((item) => {
        return item.session === previous.data;
      });

      if (upload?.result) {
        return fail(409, "M013", "이미 완료됨");
      }

      if (upload && toUploadSession(upload.session).uploadExpiresAt.getTime() <= Date.now()) {
        return fail(410, "M011", "업로드 만료");
      }

      return ok(previous.data, 201);
    }

    const uploadId = `550e8400-e29b-41d4-a716-${String(mockDb.nextUploadId++).padStart(12, "0")}`;
    const session: MockUpload["session"] = {
      uploadId, status: "UPLOADING", contentType: body.contentType, sizeBytes: body.sizeBytes,
      uploadUrl: `https://uploads.yeogirok.test/mock/${uploadId}`, uploadMethod: "PUT",
      uploadHeaders: { "Content-Type": body.contentType }, uploadExpiresAt: "2100-01-01 00:00:00+09",
    };
    mockDb.uploads.set(uploadId, { request: body, session, transferredBytes: null, result: null, acceptedAt: Date.now() });
    mockDb.uploadIdempotency.set(key, { request: text, data: session, status: 201 });
    return ok(session, 201);
  }),
  http.put("https://uploads.yeogirok.test/mock/:uploadId", async ({ request, params }) => {
    const upload = mockDb.uploads.get(String(params.uploadId));

    if (!upload || request.headers.has("Authorization")) {
      return new Response(null, { status: 403 });
    }

    const bytes = await request.arrayBuffer();
    upload.transferredBytes = bytes.byteLength;

    if (upload.request.purpose !== "TIP_PHOTO") {
      upload.bytes = bytes;
    }

    return new Response(null, { status: 200 });
  }),
  http.post("*/api/v1/uploads/:uploadId/complete", async ({ request, params }) => {
    if (!request.headers.get("Authorization")?.startsWith("Bearer ")) {
      return fail(401, "A001", "인증 필요");
    }

    const key = request.headers.get("Idempotency-Key");

    if (!key) {
      return fail(400, "C001", "Idempotency-Key 필요");
    }

    let uploadId: string;

    try {
      uploadId = assertUploadId(params.uploadId);
    } catch {
      return fail(400, "C002", "uploadId 형식 오류");
    }

    const upload = mockDb.uploads.get(uploadId);

    if (!upload) {
      return fail(404, "M004", "업로드 없음");
    }

    const previous = mockDb.uploadIdempotency.get(`complete:${key}`);

    if (previous && previous.request !== uploadId) {
      return fail(409, "C005", "멱등키 충돌");
    }

    if (upload.result) {
      return ok(upload.result);
    }

    if (toUploadSession(upload.session).uploadExpiresAt.getTime() <= Date.now()) {
      return fail(410, "M011", "업로드 만료");
    }

    if (upload.transferredBytes === null) {
      return fail(422, "M006", "전송 미완료");
    }

    if (upload.transferredBytes !== upload.request.sizeBytes) {
      return fail(422, "M003", "파일 크기 불일치");
    }

    upload.result = {
      uploadId, status: "READY", imageId: upload.request.purpose === "RECORD_PHOTO" ? mockDb.nextRecordEntityId++ : null,
      materialId: upload.request.purpose === "RECEIPT" ? uploadId : null,
      contentType: upload.request.contentType, sizeBytes: upload.transferredBytes,
    };
    mockDb.uploadIdempotency.set(`complete:${key}`, { request: uploadId, data: upload.result, status: 200 });
    return ok(upload.result);
  }),
];
