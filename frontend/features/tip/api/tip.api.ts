import { api, TIMEOUT_MUTATION_MS } from "@/api/client";
import { ApiError } from "@/api/errors";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import {
  normalizeTipBody, toMyTipDetail, toMyTipsPage, toTipCreated, toTipDeleted, toTipDetail,
  toTipOperation, toTipReactionResponse, toTipSearchPage, toTipStatus, toTipRetryCompleted,
  validateTipLocation, validateTipSpatialFields, validateTipUploads, validateTipType,
  type CreateTipRequestDto, type LocationEvidenceDto, type MyTipsRequestDto,
  type RetryTipRequestDto, type SearchTipsRequestDto, type TipReactionRequest, type UpdateTipRequestDto,
} from "@/features/tip/model";
import { assertId } from "@/lib/contract";

export type CreateTipInput = { request: CreateTipRequestDto; idempotencyKey: string };
export type UpdateTipInput = { tipId: number; request: UpdateTipRequestDto; idempotencyKey: string };
export type RetryTipInput = { tipId: number; request: RetryTipRequestDto; idempotencyKey: string };
export type ViewTipInput = { tipId: number; location: LocationEvidenceDto };
export type TipReactionInput = ViewTipInput & { reaction: TipReactionRequest };
export type DeleteTipInput = { tipId: number; version: number };

function mutationConfig(idempotencyKey: string) {
  if (!idempotencyKey.trim()) {
    throw new ApiError(400, "C001", "Idempotency-Key가 필요합니다.");
  }

  return { timeout: TIMEOUT_MUTATION_MS, headers: { [IDEMPOTENCY_HEADER]: idempotencyKey } };
}

export async function searchTips(request: SearchTipsRequestDto, signal?: AbortSignal) {
  if (request.limit !== undefined && (!Number.isSafeInteger(request.limit) || request.limit < 1 || request.limit > 10)) {
    throw new ApiError(400, "C003", "주변 팁은 가까운 순 최대 10개까지 조회합니다.");
  }

  if (request.radiusMeters !== undefined && request.radiusMeters !== 50) {
    throw new ApiError(400, "C003", "팁 탐색 반경은 50m입니다.");
  }

  const { data } = await api.post<unknown>("/tips/search", {
    location: validateTipLocation(request.location),
    ...(request.radiusMeters === undefined ? {} : { radiusMeters: request.radiusMeters }),
    ...(request.cursor === undefined ? {} : { cursor: request.cursor }),
    ...(request.limit === undefined ? {} : { limit: request.limit }),
  }, { signal });
  return toTipSearchPage(data);
}

export async function viewTip({ tipId, location }: ViewTipInput, signal?: AbortSignal) {
  const { data } = await api.post<unknown>(`/tips/${assertId(tipId, "tipId")}/view`, { location: validateTipLocation(location) }, { signal });
  return toTipDetail(data);
}

export async function createTip({ request, idempotencyKey }: CreateTipInput) {
  validateTipSpatialFields(request, true);
  const { data } = await api.post<unknown>("/tips", {
    category: validateTipType(request.category),
    body: normalizeTipBody(request.body),
    ...(request.uploadIds === undefined ? {} : { uploadIds: validateTipUploads(request.uploadIds) }),
    location: validateTipLocation(request.location),
    spatialReference: request.spatialReference,
    placement: request.placement,
  }, mutationConfig(idempotencyKey));
  return toTipCreated(data);
}

export async function getTipStatus(tipId: number, signal?: AbortSignal) {
  const { data } = await api.get<unknown>(`/tips/${assertId(tipId, "tipId")}/status`, { signal });
  return toTipStatus(data);
}

export async function retryTip({ tipId, request, idempotencyKey }: RetryTipInput) {
  const { data, status } = await api.post<unknown>(`/tips/${assertId(tipId, "tipId")}/retry`, {
    ...(request.location === undefined ? {} : { location: validateTipLocation(request.location) }),
    ...(request.replacementUploadIds === undefined ? {} : { replacementUploadIds: validateTipUploads(request.replacementUploadIds) }),
    ...(request.spatialReference === undefined ? {} : { spatialReference: request.spatialReference }),
    ...(request.placement === undefined ? {} : { placement: request.placement }),
  }, mutationConfig(idempotencyKey));
  return status === 200 ? { ...toTipRetryCompleted(data), completed: true as const } : { ...toTipOperation(data), completed: false as const };
}

export async function setTipReaction({ tipId, reaction, location }: TipReactionInput) {
  const { data } = await api.put<unknown>(`/tips/${assertId(tipId, "tipId")}/reaction`, {
    reaction, location: validateTipLocation(location),
  }, { timeout: TIMEOUT_MUTATION_MS });
  return toTipReactionResponse(data);
}

export async function getMyTips(request: MyTipsRequestDto = {}, signal?: AbortSignal) {
  const { data } = await api.get<unknown>("/users/me/tips", { params: request, signal });
  return toMyTipsPage(data);
}

export async function getMyTip(tipId: number, signal?: AbortSignal) {
  const { data } = await api.get<unknown>(`/users/me/tips/${assertId(tipId, "tipId")}`, { signal });
  return toMyTipDetail(data);
}

export async function updateTip({ tipId, request, idempotencyKey }: UpdateTipInput) {
  const spatial = validateTipSpatialFields(request, false);

  if (request.body === undefined && request.category === undefined && request.uploadIds === undefined && spatial.location === undefined) {
    throw new ApiError(400, "C003", "변경할 내용이 없습니다.");
  }

  const { data } = await api.patch<unknown>(`/tips/${assertId(tipId, "tipId")}`, {
    version: assertId(request.version, "version"),
    ...(request.category === undefined ? {} : { category: validateTipType(request.category) }),
    ...(request.body === undefined ? {} : { body: normalizeTipBody(request.body) }),
    ...(request.uploadIds === undefined ? {} : { uploadIds: validateTipUploads(request.uploadIds) }),
    ...spatial,
  }, mutationConfig(idempotencyKey));
  return toTipOperation(data, true);
}

export async function deleteTip({ tipId, version }: DeleteTipInput) {
  const { data } = await api.delete<unknown>(`/tips/${assertId(tipId, "tipId")}`, {
    params: { version: assertId(version, "version") }, timeout: TIMEOUT_MUTATION_MS,
  });
  return toTipDeleted(data);
}
