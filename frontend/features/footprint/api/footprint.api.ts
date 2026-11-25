import { api, idempotentRequestConfig, TIMEOUT_MUTATION_MS } from "@/api/client";
import { ApiError } from "@/api/errors";
import {
  countFootprintBody,
  FOOTPRINT_BODY_MAX_LENGTH,
  normalizeGuestbookBody,
  toFootprintDelete,
  toFootprintDetail,
  toFootprintList,
  toGuestbookUpdate,
  toSavedFootprint,
  type CreateFootprintRequestDto,
  type CreateFootprintResponseDto,
  type FootprintDeleteDto,
  type FootprintDetailDto,
  type FootprintListDto,
  type FootprintListParams,
  type GuestbookUpdateDto,
  type SavedFootprint,
  type UpdateGuestbookDto,
} from "@/features/footprint/model";

export type CreateFootprintInput = { request: CreateFootprintRequestDto; idempotencyKey: string };

/** API-041 현재 위치 발자취·방명록 저장. 같은 내용을 다시 보낼 때는 같은 멱등키를 써서 두 번 저장되지 않게 한다. */
export async function createFootprint({ request, idempotencyKey }: CreateFootprintInput): Promise<SavedFootprint> {
  const { data } = await api.post<CreateFootprintResponseDto>("/footprints", request, idempotentRequestConfig(idempotencyKey));
  return toSavedFootprint(data);
}

export async function getFootprints(params: FootprintListParams, signal?: AbortSignal) {
  const { data } = await api.get<FootprintListDto>("/users/me/footprints", { params, signal });
  return toFootprintList(data);
}

export async function getFootprint(id: number, signal?: AbortSignal) {
  const { data } = await api.get<FootprintDetailDto>(`/footprints/${id}`, { signal });
  return toFootprintDetail(data);
}

export async function updateGuestbook({ id, version, body }: { id: number } & UpdateGuestbookDto) {
  if (countFootprintBody(body) > FOOTPRINT_BODY_MAX_LENGTH) {
    throw new ApiError(400, "C003", "방명록 본문 길이 초과");
  }

  const { data } = await api.patch<GuestbookUpdateDto>(`/footprints/${id}`, {
    version,
    body: normalizeGuestbookBody(body),
  }, { timeout: TIMEOUT_MUTATION_MS });
  return toGuestbookUpdate(data);
}

export async function deleteFootprint({ id, version }: { id: number; version: number }) {
  const { data } = await api.delete<FootprintDeleteDto>(`/footprints/${id}`, {
    params: { version },
    timeout: TIMEOUT_MUTATION_MS,
  });
  return toFootprintDelete(data);
}
