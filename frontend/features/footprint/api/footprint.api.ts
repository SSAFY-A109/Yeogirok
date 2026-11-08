import { api, TIMEOUT_MUTATION_MS } from "@/api/client";
import { ApiError } from "@/api/errors";
import {
  GUESTBOOK_LIMIT, guestbookLength, normalizeGuestbookBody, toFootprintDelete, toFootprintDetail, toFootprintList, toGuestbookUpdate,
  type FootprintDeleteDto, type FootprintDetailDto, type FootprintListDto, type FootprintListParams, type GuestbookUpdateDto, type UpdateGuestbookDto,
} from "../model";

export async function getFootprints(params: FootprintListParams, signal?: AbortSignal) {
  const { data } = await api.get<FootprintListDto>("/users/me/footprints", { params, signal });
  return toFootprintList(data);
}

export async function getFootprint(id: number, signal?: AbortSignal) {
  const { data } = await api.get<FootprintDetailDto>(`/footprints/${id}`, { signal });
  return toFootprintDetail(data);
}

export async function updateGuestbook({ id, version, body }: { id: number } & UpdateGuestbookDto) {
  if (guestbookLength(body) > GUESTBOOK_LIMIT) {
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
