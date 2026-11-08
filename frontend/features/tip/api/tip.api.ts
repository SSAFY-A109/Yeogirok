import { api, TIMEOUT_MUTATION_MS } from "@/api/client";
import { ApiError } from "@/api/errors";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import {
  normalizeTipBody, toMyTipDetail, toMyTipsPage, toTipDeleted, toTipOperation, toTipStatus, validateTipType, validateTipUploads,
  type MyTipsRequestDto, type UpdateTipRequestDto,
} from "../model";

export type UpdateTipInput = { tipId: number; request: UpdateTipRequestDto; idempotencyKey: string };
export type DeleteTipInput = { tipId: number; version: number };

export async function getMyTips(params: MyTipsRequestDto, signal?: AbortSignal) {
  const { data } = await api.get<unknown>("/users/me/tips", { params, signal });
  return toMyTipsPage(data);
}

export async function getMyTip(tipId: number, signal?: AbortSignal) {
  const { data } = await api.get<unknown>(`/users/me/tips/${tipId}`, { signal });
  return toMyTipDetail(data);
}

export async function getTipStatus(tipId: number, signal?: AbortSignal) {
  const { data } = await api.get<unknown>(`/tips/${tipId}/status`, { signal });
  return toTipStatus(data);
}

export async function updateTip({ tipId, request, idempotencyKey }: UpdateTipInput) {
  if (request.category === undefined && request.body === undefined && request.uploadIds === undefined) {
    throw new ApiError(400, "C003", "변경할 내용이 없습니다.");
  }

  const { data } = await api.patch<unknown>(`/tips/${tipId}`, {
    version: request.version,
    category: request.category === undefined ? undefined : validateTipType(request.category),
    body: request.body === undefined ? undefined : normalizeTipBody(request.body),
    uploadIds: validateTipUploads(request.uploadIds),
  }, { headers: { [IDEMPOTENCY_HEADER]: idempotencyKey }, timeout: TIMEOUT_MUTATION_MS });
  return toTipOperation(data);
}

export async function deleteTip({ tipId, version }: DeleteTipInput) {
  const { data } = await api.delete<unknown>(`/tips/${tipId}`, { params: { version }, timeout: TIMEOUT_MUTATION_MS });
  return toTipDeleted(data);
}
