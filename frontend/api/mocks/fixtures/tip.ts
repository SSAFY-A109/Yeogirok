import type { CreateTipRequestDto, TipDetailDto, TipStatusDto, UpdateTipRequestDto } from "../../../features/tip/model";

export const tipFixture: TipDetailDto = {
  category: "PHOTO",
  id: 1,
  body: "입구 오른쪽에서 사진을 찍으면 좋아요.",
  createdAt: "2026-11-01 09:00:00+09",
  publishedAt: "2026-11-01 09:00:05+09",
  photos: [],
  author: { id: 2, nickname: "여행자" },
  location: { latitude: 37.5665, longitude: 126.978 },
  spatialReference: { provider: "ARCORE_CLOUD_ANCHOR", referenceId: "mock-spatial-reference", expiresAt: "2100-01-01 00:00:00+09" },
  placement: { schemaVersion: 1, anchorType: "CLOUD", authorizationMode: "KEYLESS" },
  reactions: { likes: 1, dislikes: 0, myReaction: null },
};

export type MockTip = {
  detail: TipDetailDto;
  state: TipStatusDto;
  pending: CreateTipRequestDto | UpdateTipRequestDto | null;
  uploadIds: string[];
  checks: number;
};

export function createTipFixtures(): Map<number, MockTip> {
  return new Map([1, 2].map((id) => {
    const detail = { ...tipFixture, id, author: { id: id === 1 ? 2 : 1, nickname: id === 1 ? "여행자" : "나" }, reactions: { ...tipFixture.reactions } };
    return [id, {
      detail,
      state: { tipId: id, status: "PUBLISHED", jobId: null, steps: { media: "SKIPPED", spatial: "SUCCEEDED", content: "SUCCEEDED" }, error: null, retryable: false, operation: null, operationStatus: null, version: 1 },
      pending: null, uploadIds: [], checks: 0,
    }];
  }));
}
