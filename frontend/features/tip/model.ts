import { ApiError } from "../../api/errors";
import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";
import { parseServerDateTime } from "../../lib/date";

export const TIP_BODY_MAX_LENGTH = 120;
export const TIP_TYPE_LABELS = { PHOTO: "포토", RESTAURANT: "맛집", CAFE: "카페", TIP: "꿀팁", TMI: "TMI" } as const;
export type TipTypeDto = keyof typeof TIP_TYPE_LABELS;
export const TIP_TYPE_VALUES = ["PHOTO", "RESTAURANT", "CAFE", "TIP", "TMI"] as const;

export function toTipType(value: unknown) {
  return toKnownEnum(value, TIP_TYPE_VALUES);
}

export function validateTipType(value: unknown): TipTypeDto {
  if (value === null || value === undefined) {
    throw new ApiError(400, "C001", "팁 종류가 필요합니다.");
  }

  if (typeof value !== "string") {
    throw new ApiError(400, "C002", "팁 종류는 문자열이어야 합니다.");
  }

  const tipType = toTipType(value);

  if (tipType === "UNKNOWN") {
    throw new ApiError(400, "C003", "팁 종류가 올바르지 않습니다.");
  }

  return tipType;
}

export type TipStatus = "REGISTERING" | "PUBLISHED" | "FAILED" | "DELETED" | "UNKNOWN";
export type TipOperation = "CREATE" | "UPDATE" | "UNKNOWN" | null;
export type TipOperationStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "UNKNOWN" | null;
export type UpdateTipRequestDto = {
  category?: TipTypeDto;
  version: number;
  body?: string;
  uploadIds?: string[];
};
export type MyTipsRequestDto = { status?: string; cursor?: string; limit?: number };
export type TipStatusDto = {
  tipId: number;
  status: string;
  jobId: string | null;
  steps: { media: string; spatial: string; content: string };
  error: Record<string, unknown> | null;
  retryable: boolean;
  operation: string | null;
  operationStatus: string | null;
  version: number;
};
export type TipOperationResponseDto = {
  tipId: number;
  jobId: string;
  status: "PUBLISHED" | "REGISTERING";
  operation: "UPDATE";
  operationStatus: "QUEUED";
  version: number;
};
export type MyTipSummaryDto = { id: number; category: string; status: string; createdAt: string; updatedAt: string };
export type MyTipsResponseDto = { items: MyTipSummaryDto[]; nextCursor: string | null };
export type MyTipDetailDto = {
  category: string;
  tipId: number;
  body: string;
  photos: { imageId: number; url: string; expiresAt: string }[];
  status: string;
  version: number;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function object(value: unknown, field: string): Record<string, unknown> {
  if (!isObject(value)) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function string(value: unknown, field: string): string {
  if (typeof value !== "string") {
    throw new ContractMismatchError(field);
  }

  return value;
}

function uuid(value: unknown, field: string): string {
  const text = string(value, field);

  if (!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(text)) {
    throw new ContractMismatchError(field);
  }

  return text;
}

function tipBody(value: unknown): string {
  const body = string(value, "body");

  if (!body.trim() || Array.from(body).length > TIP_BODY_MAX_LENGTH || body.includes("\r")) {
    throw new ContractMismatchError("body");
  }

  return body;
}

function boolean(value: unknown, field: string): boolean {
  if (typeof value !== "boolean") {
    throw new ContractMismatchError(field);
  }

  return value;
}

function cursor(value: unknown): string | null {
  return value === null ? null : string(value, "nextCursor");
}

function photos(value: unknown): unknown[] {
  if (!Array.isArray(value) || value.length > 1) {
    throw new ContractMismatchError("photos");
  }

  return value;
}

function photoUrl(value: unknown): string {
  const text = string(value, "photos.url");

  try {
    const url = new URL(text);

    if (url.protocol === "https:" && !url.username && !url.password) {
      return text;
    }
  } catch {
    throw new ContractMismatchError("photos.url");
  }

  throw new ContractMismatchError("photos.url");
}

function status(value: unknown): TipStatus {
  return toKnownEnum(value, ["REGISTERING", "PUBLISHED", "FAILED", "DELETED"] as const);
}

export function toTipStatus(value: unknown) {
  const dto = object(value, "tipStatus");
  const steps = object(dto.steps, "steps");
  return {
    tipId: assertId(dto.tipId, "tipId"),
    status: status(dto.status),
    jobId: dto.jobId === null ? null : uuid(dto.jobId, "jobId"),
    steps: {
      media: toKnownEnum(steps.media, ["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "SKIPPED"] as const),
      spatial: toKnownEnum(steps.spatial, ["QUEUED", "RUNNING", "SUCCEEDED", "FAILED"] as const),
      content: toKnownEnum(steps.content, ["QUEUED", "RUNNING", "SUCCEEDED", "FAILED"] as const),
    },
    error: dto.error === null ? null : object(dto.error, "error"),
    retryable: boolean(dto.retryable, "retryable"),
    operation: dto.operation === null ? null : toKnownEnum(dto.operation, ["CREATE", "UPDATE"] as const),
    operationStatus: dto.operationStatus === null ? null : toKnownEnum(dto.operationStatus, ["QUEUED", "RUNNING", "SUCCEEDED", "FAILED"] as const),
    version: assertId(dto.version, "version"),
  };
}

export function toMyTipsPage(value: unknown) {
  const dto = object(value, "myTips");

  if (!Array.isArray(dto.items)) {
    throw new ContractMismatchError("items");
  }

  return {
    items: dto.items.map((value: unknown) => {
      const item = object(value, "items");
      return { id: assertId(item.id, "id"), tipType: toTipType(item.category), status: status(item.status), createdAt: parseServerDateTime(item.createdAt, "createdAt"), updatedAt: parseServerDateTime(item.updatedAt, "updatedAt") };
    }),

    nextCursor: cursor(dto.nextCursor),
  };
}

export function toMyTipDetail(value: unknown) {
  const dto = object(value, "myTip");
  return {
    tipType: toTipType(dto.category),
    tipId: assertId(dto.tipId, "tipId"),
    body: tipBody(dto.body),

    photos: photos(dto.photos).map((value) => {
      const photo = object(value, "photos");
      return { imageId: assertId(photo.imageId, "imageId"), url: photoUrl(photo.url), expiresAt: parseServerDateTime(photo.expiresAt, "photos.expiresAt") };
    }),

    status: status(dto.status),
    version: assertId(dto.version, "version"),
    publishedAt: dto.publishedAt === null ? null : parseServerDateTime(dto.publishedAt, "publishedAt"),
    createdAt: parseServerDateTime(dto.createdAt, "createdAt"),
    updatedAt: parseServerDateTime(dto.updatedAt, "updatedAt"),
  };
}

export function normalizeTipBody(value: unknown): string {
  if (value === null || value === undefined) {
    throw new ApiError(400, "C001", "팁 본문이 필요합니다.");
  }

  if (typeof value !== "string") {
    throw new ApiError(400, "C002", "팁 본문은 문자열이어야 합니다.");
  }

  const body = value.replace(/\r\n?/g, "\n");

  if (!body.trim() || Array.from(body).length > TIP_BODY_MAX_LENGTH) {
    throw new ApiError(400, "C003", "팁 본문은 공백만일 수 없으며 최대 120자입니다.");
  }

  return body;
}

export function validateTipUploads(value: string[] | undefined): string[] | undefined {
  if (value !== undefined && (!Array.isArray(value) || value.length > 1 || value.some((id) => {
    return typeof id !== "string" || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(id);
  }))) {
    throw new ApiError(400, "C003", "사진은 업로드 UUID 0~1개로 지정해야 합니다.");
  }

  return value;
}

export type TipPublicationStatus = ReturnType<typeof toTipStatus>;
export type MyTipDetail = ReturnType<typeof toMyTipDetail>;

export function toTipOperation(value: unknown) {
  const dto = object(value, "tipOperation");

  return {
    tipId: assertId(dto.tipId, "tipId"),
    status: status(dto.status),
    jobId: uuid(dto.jobId, "jobId"),
    operation: toKnownEnum(dto.operation, ["CREATE", "UPDATE"] as const),
    operationStatus: toKnownEnum(dto.operationStatus, ["QUEUED"] as const),
    version: assertId(dto.version, "version"),
  };
}

export function toTipDeleted(value: unknown) {
  const dto = object(value, "deleteTip");

  if (dto.status !== "DELETED") {
    throw new ContractMismatchError("status");
  }

  return { tipId: assertId(dto.tipId, "tipId"), status: status(dto.status) };
}

export function isTipOperationPending(value: TipPublicationStatus | undefined): boolean {
  return value !== undefined && (value.operationStatus === "QUEUED" || value.operationStatus === "RUNNING" ||
    (value.status === "REGISTERING" && value.operationStatus === null));
}
