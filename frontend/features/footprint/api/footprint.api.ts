import { api, idempotentRequestConfig } from "@/api/client";
import {
  toSavedFootprint,
  type CreateFootprintRequestDto,
  type CreateFootprintResponseDto,
  type SavedFootprint,
} from "@/features/footprint/model";

export type CreateFootprintInput = { request: CreateFootprintRequestDto; idempotencyKey: string };

/** API-041 현재 위치 발자취·방명록 저장. 같은 내용을 다시 보낼 때는 같은 멱등키를 써서 두 번 저장되지 않게 한다. */
export async function createFootprint({ request, idempotencyKey }: CreateFootprintInput): Promise<SavedFootprint> {
  const { data } = await api.post<CreateFootprintResponseDto>("/footprints", request, idempotentRequestConfig(idempotencyKey));
  return toSavedFootprint(data);
}
