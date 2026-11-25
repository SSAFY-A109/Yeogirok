import axios, { isAxiosError } from "axios";

import { api, TIMEOUT_MUTATION_MS, TIMEOUT_UPLOAD_MS } from "@/api/client";
import { ApiError } from "@/api/errors";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import { assertUploadId, toReadyUpload, toUploadSession, validateUploadRequest, type CreateUploadRequestDto, type UploadSession } from "@/features/media/model";
import { ContractMismatchError } from "@/lib/contract";

export type CreateUploadInput = { request: CreateUploadRequestDto; idempotencyKey: string };
export type CompleteUploadInput = { uploadId: string; idempotencyKey: string };
export type UploadBody = Blob | ArrayBuffer | Uint8Array;

export async function createUpload({ request, idempotencyKey }: CreateUploadInput) {
  const { data } = await api.post<unknown>("/uploads", validateUploadRequest(request), {
    timeout: TIMEOUT_MUTATION_MS, headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
  });
  const session = toUploadSession(data);

  if (session.contentType !== request.contentType || session.sizeBytes !== request.sizeBytes) {
    throw new ContractMismatchError("upload metadata");
  }

  return session;
}

export async function completeUpload({ uploadId, idempotencyKey }: CompleteUploadInput) {
  const { data } = await api.post<unknown>(`/uploads/${assertUploadId(uploadId)}/complete`, undefined, {
    timeout: TIMEOUT_MUTATION_MS, headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
  });
  const ready = toReadyUpload(data);

  if (ready.uploadId !== uploadId) {
    throw new ContractMismatchError("uploadId");
  }

  return ready;
}

export async function putUpload(session: UploadSession, body: UploadBody, onProgress?: (fraction: number) => void) {
  if (session.uploadExpiresAt.getTime() <= Date.now()) {
    throw new ApiError(410, "M011", "업로드 URL이 만료되었습니다.");
  }

  const sizeBytes = body instanceof ArrayBuffer || body instanceof Uint8Array ? body.byteLength : body.size;

  if (sizeBytes !== session.sizeBytes) {
    throw new ApiError(422, "M003", "업로드할 파일 크기가 세션과 다릅니다.");
  }

  // 공통 api 클라이언트의 인증·봉투 인터셉터가 S3 요청에 적용되면 안 된다.
  try {
    await axios.request({
      method: session.uploadMethod, url: session.uploadUrl, data: body instanceof Uint8Array ? Uint8Array.from(body).buffer : body,
      headers: { ...session.uploadHeaders }, timeout: TIMEOUT_UPLOAD_MS, withCredentials: false,

      onUploadProgress: ({ loaded, total }) => {
        onProgress?.(Math.min(1, loaded / (total ?? session.sizeBytes)));
      },
    });
  } catch (error) {
    throw new ApiError(isAxiosError(error) ? error.response?.status ?? 0 : 0, "S3_UPLOAD_FAILED", "S3 파일 전송에 실패했습니다.");
  }
}
