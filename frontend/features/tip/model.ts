import { ApiError } from "../../api/errors";
import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";
import { parseServerDateTime } from "../../lib/date";

export const TIP_SEARCH_RADIUS_METERS = 50;
export const TIP_INTERACTION_RADIUS_METERS = 5;
export const TIP_BODY_MAX_LENGTH = 120;
export const TIP_TYPE_LABELS = { PHOTO: "포토", FOOD: "맛집", CAFE: "카페", TIP: "꿀팁", TMI: "TMI" } as const;
export type TipTypeDto = keyof typeof TIP_TYPE_LABELS;
export const TIP_TYPE_VALUES = ["PHOTO", "FOOD", "CAFE", "TIP", "TMI"] as const;

export function toTipType(value: unknown) {
  return toKnownEnum(value, ["PHOTO", "FOOD", "CAFE", "TIP", "TMI"] as const);
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

export type LocationEvidenceDto = { latitude: number; longitude: number; accuracyMeters: number; capturedAt: string };
export type SpatialReferenceDto = { provider: string; referenceId: string; expiresAt?: string };
export type TipPlacementDto = Record<string, unknown>;
export type TipReactionRequest = "LIKE" | "DISLIKE" | "NONE";
export type TipReaction = "LIKE" | "DISLIKE" | "UNKNOWN" | null;
export type TipStatus = "REGISTERING" | "PUBLISHED" | "FAILED" | "DELETED" | "UNKNOWN";
export type TipOperation = "CREATE" | "UPDATE" | "UNKNOWN" | null;
export type TipOperationStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "UNKNOWN" | null;

export type SearchTipsRequestDto = {
  location: LocationEvidenceDto;
  radiusMeters?: 50;
  cursor?: string;
  limit?: number;
};
export type CreateTipRequestDto = {
  category: TipTypeDto;
  body: string;
  uploadIds?: string[];
  location: LocationEvidenceDto;
  spatialReference: SpatialReferenceDto;
  placement: TipPlacementDto;
};
export type UpdateTipRequestDto = {
  category?: TipTypeDto;
  version: number;
  body?: string;
  uploadIds?: string[];
  location?: LocationEvidenceDto;
  spatialReference?: SpatialReferenceDto;
  placement?: TipPlacementDto;
};
export type RetryTipRequestDto = {
  location?: LocationEvidenceDto;
  replacementUploadIds?: string[];
  spatialReference?: SpatialReferenceDto;
  placement?: TipPlacementDto;
};
export type MyTipsRequestDto = { status?: string; cursor?: string; limit?: number };

export type TipSearchItemDto = {
  category: string;
  id: number;
  latitude: number;
  longitude: number;
  summary: string;
  spatialReference: SpatialReferenceDto;
  placement: TipPlacementDto;
  distanceMeters: number;
  canInteract: boolean;
};
export type SearchTipsResponseDto = {
  items: TipSearchItemDto[];
  nextCursor: string | null;
  appliedRadiusMeters: 50;
  totalCount: number;
  interactionRadiusMeters: 5;
};
export type PublicTipPhotoDto = { imageId: number; url: string; attribution: string | null };
export type TipReactionsDto = { likes: number; dislikes: number; myReaction: string | null };
export type TipDetailDto = {
  category: string;
  id: number;
  body: string;
  createdAt: string;
  publishedAt: string;
  photos: PublicTipPhotoDto[];
  author: { id: number | null; nickname: string };
  location: { latitude: number; longitude: number };
  spatialReference: SpatialReferenceDto;
  placement: TipPlacementDto;
  reactions: TipReactionsDto;
};
export type CreateTipResponseDto = { tipId: number; jobId: string; status: "REGISTERING" };
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
  operation: "CREATE" | "UPDATE";
  operationStatus: "QUEUED";
  version?: number;
};
export type RetryTipResponseDto = TipOperationResponseDto | { tipId: number; status: "PUBLISHED" };
export type MyTipSummaryDto = { id: number; category?: string; status: string; createdAt: string; updatedAt: string };
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

  if (!body.trim() || body.length > TIP_BODY_MAX_LENGTH || body.includes("\r")) {
    throw new ContractMismatchError("body");
  }

  return body;
}

function number(value: unknown, field: string, maximum = Infinity): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0 || value > maximum) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function count(value: unknown, field: string): number {
  return assertId(number(value, field), field);
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

export function toTipCoordinate(value: unknown) {
  const dto = object(value, "location");

  if (typeof dto.latitude !== "number" || !Number.isFinite(dto.latitude) || Math.abs(dto.latitude) > 90 ||
    typeof dto.longitude !== "number" || !Number.isFinite(dto.longitude) || Math.abs(dto.longitude) > 180) {
    throw new ContractMismatchError("location");
  }

  return { latitude: dto.latitude, longitude: dto.longitude };
}

export function toSpatialReference(value: unknown) {
  const dto = object(value, "spatialReference");
  const provider = string(dto.provider, "spatialReference.provider");
  const referenceId = string(dto.referenceId, "spatialReference.referenceId");

  if (!provider.trim() || !referenceId.trim()) {
    throw new ContractMismatchError("spatialReference");
  }

  if (provider === "ARCORE_CLOUD_ANCHOR" && (referenceId.length > 255 || /\s/.test(referenceId) || dto.expiresAt === undefined)) {
    throw new ContractMismatchError("spatialReference");
  }

  return { provider, referenceId, ...(dto.expiresAt === undefined ? {} : { expiresAt: parseServerDateTime(dto.expiresAt, "spatialReference.expiresAt") }) };
}

export function toTipReactions(value: unknown) {
  const dto = object(value, "reactions");
  return {
    likes: count(dto.likes, "likes"),
    dislikes: count(dto.dislikes, "dislikes"),
    myReaction: dto.myReaction === null ? null : toKnownEnum(dto.myReaction, ["LIKE", "DISLIKE"] as const),
  };
}

export function toTipSearchPage(value: unknown) {
  const dto = object(value, "tipSearch");

  if (!Array.isArray(dto.items) || dto.items.length > 10 || dto.appliedRadiusMeters !== 50 || dto.interactionRadiusMeters !== 5) {
    throw new ContractMismatchError("tipSearch");
  }

  const items = dto.items.map((value: unknown) => {
    const item = object(value, "items");
    const distanceMeters = number(item.distanceMeters, "distanceMeters", TIP_SEARCH_RADIUS_METERS);
    const canInteract = boolean(item.canInteract, "canInteract");

    if (canInteract !== (distanceMeters <= TIP_INTERACTION_RADIUS_METERS)) {
      throw new ContractMismatchError("canInteract");
    }

    return {
      tipType: toTipType(item.category),
      id: assertId(item.id, "id"),
      ...toTipCoordinate(item),
      summary: string(item.summary, "summary"),
      spatialReference: toSpatialReference(item.spatialReference),
      placement: object(item.placement, "placement"),
      distanceMeters,
      canInteract,
    };
  });

  return { items, nextCursor: cursor(dto.nextCursor), totalCount: count(dto.totalCount, "totalCount"), appliedRadiusMeters: 50, interactionRadiusMeters: 5 };
}

export function toTipDetail(value: unknown) {
  const dto = object(value, "tipDetail");
  const author = object(dto.author, "author");
  return {
    tipType: toTipType(dto.category),
    id: assertId(dto.id, "id"),
    body: tipBody(dto.body),
    createdAt: parseServerDateTime(dto.createdAt, "createdAt"),
    publishedAt: parseServerDateTime(dto.publishedAt, "publishedAt"),

    photos: photos(dto.photos).map((value) => {
      const photo = object(value, "photos");
      return { imageId: assertId(photo.imageId, "imageId"), url: photoUrl(photo.url), attribution: photo.attribution === null ? null : string(photo.attribution, "attribution") };
    }),

    author: { id: author.id === null ? null : assertId(author.id, "author.id"), nickname: string(author.nickname, "author.nickname") },
    location: toTipCoordinate(dto.location),
    spatialReference: toSpatialReference(dto.spatialReference),
    placement: object(dto.placement, "placement"),
    reactions: toTipReactions(dto.reactions),
  };
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
    version: count(dto.version, "version"),
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
    version: count(dto.version, "version"),
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

  if (!body.trim() || body.length > TIP_BODY_MAX_LENGTH) {
    throw new ApiError(400, "C003", "팁 본문은 공백만일 수 없으며 최대 120자입니다.");
  }

  return body;
}

export function validateTipLocation(location: LocationEvidenceDto): LocationEvidenceDto {
  if (location === null || location === undefined) {
    throw new ApiError(422, "C008", "현재 위치 증거가 필요합니다.");
  }

  toTipCoordinate(location);
  number(location.accuracyMeters, "location.accuracyMeters");
  parseServerDateTime(location.capturedAt, "location.capturedAt");
  return { latitude: location.latitude, longitude: location.longitude, accuracyMeters: location.accuracyMeters, capturedAt: location.capturedAt };
}

export function validateTipUploads(value: string[] | undefined): string[] | undefined {
  if (value !== undefined && (!Array.isArray(value) || value.length > 1 || value.some((id) => {
    return typeof id !== "string" || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(id);
  }))) {
    throw new ApiError(400, "C003", "사진은 업로드 UUID 0~1개로 지정해야 합니다.");
  }

  return value;
}

export function validateTipSpatialFields(request: { location?: LocationEvidenceDto; spatialReference?: SpatialReferenceDto; placement?: TipPlacementDto }, required: boolean) {
  const hasSpatial = request.location !== undefined || request.spatialReference !== undefined || request.placement !== undefined;

  if (!required && !hasSpatial) {
    return {};
  }

  if (!request.location || !request.spatialReference || !request.placement) {
    throw new ApiError(400, "C001", "재배치에는 위치·공간 참조·배치 정보가 모두 필요합니다.");
  }

  const reference = toSpatialReference(request.spatialReference);

  if (reference.provider !== "ARCORE_CLOUD_ANCHOR" || !reference.expiresAt || reference.expiresAt.getTime() <= Date.now()) {
    throw new ApiError(422, "T007", "유효한 ARCore 공간 참조가 필요합니다.");
  }

  object(request.placement, "placement");
  return { location: validateTipLocation(request.location), spatialReference: request.spatialReference, placement: request.placement };
}

export type TipSearchPage = ReturnType<typeof toTipSearchPage>;
export type TipDetail = ReturnType<typeof toTipDetail>;
export type TipPublicationStatus = ReturnType<typeof toTipStatus>;
export type MyTipDetail = ReturnType<typeof toMyTipDetail>;

export function toTipCreated(value: unknown) {
  const dto = object(value, "createTip");
  return { tipId: assertId(dto.tipId, "tipId"), jobId: uuid(dto.jobId, "jobId"), status: status(dto.status) };
}

export function toTipOperation(value: unknown, requireVersion = false) {
  const dto = object(value, "tipOperation");

  if (requireVersion) {
    count(dto.version, "version");
  }

  return {
    tipId: assertId(dto.tipId, "tipId"),
    status: status(dto.status),
    jobId: uuid(dto.jobId, "jobId"),
    operation: toKnownEnum(dto.operation, ["CREATE", "UPDATE"] as const),
    operationStatus: toKnownEnum(dto.operationStatus, ["QUEUED"] as const),
    ...(dto.version === undefined ? {} : { version: count(dto.version, "version") }),
  };
}

export function toTipDeleted(value: unknown) {
  const dto = object(value, "deleteTip");

  if (dto.status !== "DELETED") {
    throw new ContractMismatchError("status");
  }

  return { tipId: assertId(dto.tipId, "tipId"), status: status(dto.status) };
}

export function toTipRetryCompleted(value: unknown) {
  const dto = object(value, "retryTip");

  if (dto.status !== "PUBLISHED") {
    throw new ContractMismatchError("status");
  }

  return { tipId: assertId(dto.tipId, "tipId"), status: "PUBLISHED" as const };
}

export function toTipReactionResponse(value: unknown) {
  const dto = object(value, "tipReaction");
  return { tipId: assertId(dto.tipId, "tipId"), ...toTipReactions(dto) };
}

export function isTipOperationPending(value: TipPublicationStatus | undefined): boolean {
  return value !== undefined && value.status !== "DELETED" && (value.operationStatus === "QUEUED" || value.operationStatus === "RUNNING" ||
    (value.status === "REGISTERING" && value.operationStatus === null));
}
