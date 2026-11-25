import { ApiError } from "../../api/errors";
import { assertId, ContractMismatchError } from "../../lib/contract";
import { parseServerDateTime } from "../../lib/date";

export type UploadPurpose = "TIP_PHOTO" | "RECORD_PHOTO" | "RECEIPT";
export type CreateUploadRequestDto = {
  purpose: UploadPurpose;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  roomId?: number;
  recordId?: number;
};
export type UploadSessionDto = {
  uploadId: string;
  status: "UPLOADING";
  contentType: string;
  sizeBytes: number;
  uploadUrl: string;
  uploadMethod: "PUT";
  uploadHeaders: Record<string, string>;
  uploadExpiresAt: string;
};
export type ReadyUploadDto = {
  uploadId: string;
  status: "READY";
  imageId: number | null;
  materialId: string | null;
  contentType: string;
  sizeBytes: number;
};

export function assertUploadId(value: unknown): string {
  if (typeof value !== "string" || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value)) {
    throw new ContractMismatchError("uploadId");
  }

  return value;
}

export function validateUploadRequest(request: CreateUploadRequestDto): CreateUploadRequestDto {
  if (!["TIP_PHOTO", "RECORD_PHOTO", "RECEIPT"].includes(request.purpose) ||
    !Number.isSafeInteger(request.sizeBytes) || request.sizeBytes <= 0 || !request.fileName.trim()) {
    throw new ApiError(400, "C003", "업로드 메타데이터가 올바르지 않습니다.");
  }

  if (!["image/jpeg", "image/png", "image/webp"].includes(request.contentType)) {
    throw new ApiError(415, "M002", "JPEG·PNG·WEBP만 업로드할 수 있습니다.");
  }

  if (request.purpose !== "TIP_PHOTO" && request.recordId === undefined) {
    throw new ApiError(400, "C001", "기록 사진·영수증에는 recordId가 필요합니다.");
  }

  if (request.recordId !== undefined) {
    assertId(request.recordId, "recordId");
  }

  if (request.roomId !== undefined) {
    assertId(request.roomId, "roomId");
  }

  return request;
}

function validateFileMetadata(value: unknown): { contentType: string; sizeBytes: number } & Record<string, unknown> {
  if (typeof value !== "object" || value === null || !("contentType" in value) || typeof value.contentType !== "string" ||
    !("sizeBytes" in value) || typeof value.sizeBytes !== "number" || !Number.isSafeInteger(value.sizeBytes) || value.sizeBytes <= 0) {
    throw new ContractMismatchError("upload");
  }

  return { ...value, contentType: value.contentType, sizeBytes: value.sizeBytes };
}

export function toUploadSession(value: unknown) {
  const dto = validateFileMetadata(value);

  if (dto.status !== "UPLOADING" || dto.uploadMethod !== "PUT" || typeof dto.uploadUrl !== "string" ||
    typeof dto.uploadHeaders !== "object" || dto.uploadHeaders === null || Array.isArray(dto.uploadHeaders)) {
    throw new ContractMismatchError("uploadSession");
  }

  const url = new URL(dto.uploadUrl);

  if (url.protocol !== "https:" || url.username || url.password) {
    throw new ContractMismatchError("uploadUrl");
  }

  const uploadHeaders: Record<string, string> = {};

  for (const [key, value] of Object.entries(dto.uploadHeaders)) {
    if (typeof value !== "string") {
      throw new ContractMismatchError("uploadHeaders");
    }

    uploadHeaders[key] = value;
  }

  return {
    uploadId: assertUploadId(dto.uploadId), status: "UPLOADING" as const,
    contentType: dto.contentType, sizeBytes: dto.sizeBytes,
    uploadUrl: dto.uploadUrl, uploadMethod: "PUT" as const, uploadHeaders,
    uploadExpiresAt: parseServerDateTime(dto.uploadExpiresAt, "uploadExpiresAt"),
  };
}

export function toReadyUpload(value: unknown) {
  const dto = validateFileMetadata(value);

  if (dto.status !== "READY") {
    throw new ContractMismatchError("status");
  }

  return {
    uploadId: assertUploadId(dto.uploadId), status: "READY" as const,
    imageId: dto.imageId === null ? null : assertId(dto.imageId, "imageId"),
    materialId: dto.materialId === null ? null : assertUploadId(dto.materialId),
    contentType: dto.contentType, sizeBytes: dto.sizeBytes,
  };
}

export type UploadSession = ReturnType<typeof toUploadSession>;
export type ReadyUpload = ReturnType<typeof toReadyUpload>;
